package dcraft.tool.backup;

import dcraft.db.Constants;
import dcraft.db.IConnectionManager;
import dcraft.db.request.update.UpdateRecordRequest;
import dcraft.filestore.CollectionSourceStream;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileCollection;
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.local.LocalStore;
import dcraft.filevault.Transaction;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.service.ServiceHub;
import dcraft.stream.StreamWork;
import dcraft.struct.Struct;
import dcraft.task.StateWork;
import dcraft.task.StateWorkStep;
import dcraft.task.TaskContext;
import dcraft.util.TimeUtil;

public class DatabaseWork extends StateWork {
	protected StateWorkStep initStep = null;
	protected StateWorkStep backupStep = null;
	protected StateWorkStep collectStep = null;
	protected StateWorkStep packStep = null;
	protected StateWorkStep uploadStep = null;
	protected StateWorkStep finStep = null;
	
	protected Transaction tx = Transaction.of("NodeDatabase");		// do not connect to a vault
	protected LocalStore fsd = null;
	protected FileCollection collection = new FileCollection();
	protected long since = -1;		// in seconds not millis
	
	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this.withSteps(
				initStep = StateWorkStep.of("Initialize File Store", this::init),
				backupStep = StateWorkStep.of("Backup Store", this::backup),
				collectStep = StateWorkStep.of("Collect Files", this::collectFiles),
				packStep = StateWorkStep.of("Copy Files", this::copyFiles),
				uploadStep = StateWorkStep.of("Commit Files", this::commitFiles),
				finStep = StateWorkStep.of("Finish", this::finish)
		);
	}
	
	public StateWorkStep init(TaskContext trun) throws OperatingContextException {
		IConnectionManager conn = ResourceHub.getResources().getDatabases().getDatabase();
		
		if (conn == null) {
			Logger.info("No database found for backup.");
			return StateWorkStep.STOP;
		}
		
		this.since = conn.lastBackup();
		
		if (this.since == -1) {
			Logger.info("Backup not enabled for database.");
			return StateWorkStep.STOP;
		}
		
		this.fsd = LocalStore.of(conn.getBackupPath());
		
		return StateWorkStep.NEXT;
	}
	
	public StateWorkStep backup(TaskContext trun) throws OperatingContextException {
		ResourceHub.getResources().getDatabases().getDatabase().backup();
		
		return StateWorkStep.NEXT;
	}
	
	
	public StateWorkStep collectFiles(TaskContext trun) throws OperatingContextException {
		// TODO add a concept of Done to Scanner
		this.fsd.scanner(CommonPath.ROOT).forEach(new OperationOutcome<FileStoreFile>() {
			@Override
			public void callback(FileStoreFile result) throws OperatingContextException {
				if (result.getModificationAsTime().toEpochSecond() <= DatabaseWork.this.since)
					return;
				
				Logger.info("Collect for backup: " + result.getPath());
				
				DatabaseWork.this.collection.withFiles(result);
			}
		});
		
		return StateWorkStep.NEXT;
	}
	
	public StateWorkStep copyFiles(TaskContext trun) throws OperatingContextException {
		if (this.collection.getSize() == 0)
			return StateWorkStep.STOP;
		
		return this.chainThenNext(trun, StreamWork.of(CollectionSourceStream.of(this.collection),
				tx.getFolder().rootFolder().allocStreamDest()));
	}
	
	public StateWorkStep commitFiles(TaskContext trun) throws OperatingContextException {
		this.tx.commitTransaction(new OperationOutcomeEmpty() {
			@Override
			public void callback() throws OperatingContextException {
				DatabaseWork.this.transition(trun, StateWorkStep.NEXT);
				
			}
		});
		
		return StateWorkStep.WAIT;
	}
	
	public StateWorkStep finish(TaskContext trun) throws OperatingContextException {
		ServiceHub.call(UpdateRecordRequest.update()
				.withTable("dcTenant")
				.withId(Constants.DB_GLOBAL_ROOT_RECORD)
				.withUpdateField("dcLastBackup", "Database", TimeUtil.now())
				.toServiceRequest()
				.withOutcome(new OperationOutcomeStruct() {
					@Override
					public void callback(Struct result) throws OperatingContextException {
						if (this.hasErrors()) {
							Logger.error("Unable to backup database, error writing database.");
						}
						
						DatabaseWork.this.transition(trun, StateWorkStep.NEXT);
					}
				})
		);
		
		return StateWorkStep.WAIT;
	}
}
