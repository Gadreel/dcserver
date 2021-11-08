package dcraft.tool.backup;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.TimeZone;

import dcraft.db.Constants;
import dcraft.db.request.query.LoadRecordRequest;
import dcraft.db.request.query.SelectFields;
import dcraft.db.request.update.UpdateRecordRequest;
import dcraft.filestore.CollectionSourceStream;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileCollection;
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.local.LocalStore;
import dcraft.filevault.Transaction;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.service.ServiceHub;
import dcraft.stream.StreamUtil;
import dcraft.stream.StreamWork;
import dcraft.struct.BaseStruct;
import dcraft.struct.Struct;
import dcraft.task.StateWork;
import dcraft.task.StateWorkStep;
import dcraft.task.TaskContext;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.util.TimeUtil;

public class LogsWork extends StateWork {
	protected StateWorkStep collectStep = null;
	protected StateWorkStep packStep = null;
	protected StateWorkStep uploadStep = null;
	protected StateWorkStep cleanStep = null;
	protected StateWorkStep finStep = null;
	protected StateWorkStep notify = null;
	
	protected Transaction tx = Transaction.of("NodeLogs");		// do not connect to a vault
	protected LocalStore fsd = LocalStore.of("./logs");
	protected FileCollection collection = new FileCollection();
	protected FileCollection deletecollection = new FileCollection();
	
	protected ZonedDateTime rm = TimeUtil.now().minusDays(5);
	protected ZonedDateTime since = null;
	protected ZonedDateTime until = TimeUtil.now().minusHours(1);
	
	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this.withSteps(
				StateWorkStep.of("Init", this::init),
				collectStep = StateWorkStep.of("Collect Files", this::collectFiles),
				packStep = StateWorkStep.of("Copy Files", this::copyFiles),
				uploadStep = StateWorkStep.of("Commit Files", this::commitFiles),
				cleanStep = StateWorkStep.of("Clean Files", this::cleanFiles),
				finStep = StateWorkStep.of("Finish", this::finish),
				notify = StateWorkStep.of("Notify", this::notify)
		);
	}
	
	public StateWorkStep init (TaskContext trun) throws OperatingContextException {
		ServiceHub.call(LoadRecordRequest.of("dcTenant")
				.withId(Constants.DB_GLOBAL_ROOT_RECORD)
				.withSelect(SelectFields.select()
						.withSubField("dcLastBackup", "Logs", "Since")
				)
				.toServiceRequest()
				.withOutcome(new OperationOutcomeStruct() {
					@Override
					public void callback(BaseStruct result) throws OperatingContextException {
						if (this.hasErrors()) {
							Logger.error("Unable to backup logs, error reading database.");
							LogsWork.this.transition(trun, StateWorkStep.STOP);
							return;
						}
						
						LogsWork.this.since = ((RecordStruct) result).getFieldAsDateTime("Since");
						
						LogsWork.this.transition(trun, collectStep);
					}
				})
		);
		
		// always add the counter stats if present
		Path stats = Paths.get("./logs/stats.json");
		
		if (Files.exists(stats)) {
			FileStoreFile f = StreamUtil.localFile(stats);
			
			this.collection.withFiles(f);
			this.deletecollection.withFiles(f);
		}
		
		return StateWorkStep.WAIT;
	}
	
	public StateWorkStep collectFiles(TaskContext trun) throws OperatingContextException {
		if (this.since == null)
			this.since = ZonedDateTime.of(1970, 1, 1, 0, 0, 0, 0, TimeZone.getDefault().toZoneId());		// from 1970
		
		// TODO add a concept of Done to Scanner
		this.fsd.scanner(CommonPath.ROOT).forEach(new OperationOutcome<FileStoreFile>() {
			@Override
			public void callback(FileStoreFile result) throws OperatingContextException {
				// collect only logs
				if (! result.getName().endsWith(".log"))
					return;
				
				ZonedDateTime lastmod = result.getModificationAsTime();		// it would be even better to parse the log name for dates, last mod is sketchy solution
				
				if (lastmod.isBefore(LogsWork.this.rm)) {
					Logger.info("Collect for delete: " + result.getPath());
					
					LogsWork.this.deletecollection.withFiles(result);
				}
				else if (lastmod.isAfter(LogsWork.this.since) && lastmod.isBefore(LogsWork.this.until)) {
					Logger.info("Collect for backup: " + result.getPath());
					
					LogsWork.this.collection.withFiles(result);
				}
			}
		});
		
		return StateWorkStep.NEXT;
	}
	
	public StateWorkStep copyFiles(TaskContext trun) throws OperatingContextException {
		if (this.collection.getSize() == 0)
			return this.cleanStep;
		
		return this.chainThenNext(trun, StreamWork.of(CollectionSourceStream.of(this.collection))
				.with(tx.getFolder().rootFolder().allocStreamDest()));
	}
	
	public StateWorkStep commitFiles(TaskContext trun) throws OperatingContextException {
		this.tx.commitTransaction(new OperationOutcomeEmpty() {
			@Override
			public void callback() throws OperatingContextException {
				LogsWork.this.transition(trun, StateWorkStep.NEXT);
			}
		});
		
		return StateWorkStep.WAIT;
	}
	
	public StateWorkStep cleanFiles(TaskContext trun) throws OperatingContextException {
		/* only delete collection
		this.collection.resetPosition();
		
		this.collection.forEach(new OperationOutcome<FileStoreFile>() {
			@Override
			public void callback(FileStoreFile result) throws OperatingContextException {
				result.remove(null);		// TODO cheating because it is local, fix
			}
		});
		*/
		
		this.deletecollection.forEach(new OperationOutcome<FileStoreFile>() {
			@Override
			public void callback(FileStoreFile result) throws OperatingContextException {
				result.remove(null);		// TODO cheating because it is local, fix
			}
		});
		
		return StateWorkStep.NEXT;
	}
	
	public StateWorkStep finish(TaskContext trun) throws OperatingContextException {
		ServiceHub.call(UpdateRecordRequest.update()
				.withTable("dcTenant")
				.withId(Constants.DB_GLOBAL_ROOT_RECORD)
				.withUpdateField("dcLastBackup", "Logs", this.until)
				.toServiceRequest()
				.withOutcome(new OperationOutcomeStruct() {
					@Override
					public void callback(BaseStruct result) throws OperatingContextException {
						if (this.hasErrors()) {
							Logger.error("Unable to backup logs, error writing database.");
						}
						
						LogsWork.this.transition(trun, StateWorkStep.NEXT);
					}
				})
		);
		
		return StateWorkStep.WAIT;
	}
	
	public StateWorkStep notify(TaskContext trun) throws OperatingContextException {
		BackupUtil.notifyProgress(ApplicationHub.getDeployment() + " : " + ApplicationHub.getNodeId() + " : logs backup done");
		
		return StateWorkStep.STOP;
	}
}
