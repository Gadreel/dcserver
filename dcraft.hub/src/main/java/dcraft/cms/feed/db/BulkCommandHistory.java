package dcraft.cms.feed.db;

import dcraft.cms.util.FeedUtil;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.proc.filter.CurrentRecord;
import dcraft.db.proc.filter.Unique;
import dcraft.db.tables.TablesAdapter;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.mem.MemoryStoreFile;
import dcraft.filevault.Vault;
import dcraft.filevault.VaultUtil;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationMarker;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.script.ScriptHub;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.ChainWork;
import dcraft.task.IWork;
import dcraft.task.Task;
import dcraft.task.TaskContext;
import dcraft.task.TaskHub;
import dcraft.task.run.WorkHub;
import dcraft.task.run.WorkTopic;
import dcraft.util.TimeUtil;
import dcraft.xml.XElement;

public class BulkCommandHistory implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		TablesAdapter db = TablesAdapter.ofNow(request);
		
		ListStruct data = request.getDataAsList();
		
		ChainWork opswork = ChainWork.chain();
		
		for (int i = 0; i < data.size(); i++) {
			RecordStruct opdata = data.getItemAsRecord(i);
			
			opswork.then(new IWork() {
				@Override
				public void run(TaskContext taskctx) throws OperatingContextException {
					String feed = opdata.getFieldAsString("Feed");
					String path = opdata.getFieldAsString("Path");
					String option = opdata.getFieldAsString("Option");
					
					if ("Save".equals(option)) {
						FeedUtilDb.saveHistory(request.getInterface(), db, feed, path, opdata, false, new OperationOutcomeStruct() {
							@Override
							public void callback(Struct result) throws OperatingContextException {
								taskctx.returnEmpty();
							}
						});
					}
					else if ("Publish".equals(option)) {
						FeedUtilDb.saveHistory(request.getInterface(), db, feed, path, opdata, true, new OperationOutcomeStruct() {
							@Override
							public void callback(Struct result) throws OperatingContextException {
								taskctx.returnEmpty();
							}
						});
					}
					else if ("Discard".equals(option)) {
						FeedUtilDb.discardHistory(request.getInterface(), db, feed, path, opdata, new OperationOutcomeStruct() {
							@Override
							public void callback(Struct result) throws OperatingContextException {
								taskctx.returnEmpty();
							}
						});
					}
					else if ("Add".equals(option)) {
						FeedUtilDb.addHistory(request.getInterface(), db, feed, path, opdata.getFieldAsList("Commands"));
						
						taskctx.returnEmpty();
					}
					else {
						Logger.warn("Unknown request in Bulk Feed History");
						
						taskctx.returnEmpty();
					}
				}
			});
		}
		
		opswork.then(new IWork() {
			@Override
			public void run(TaskContext taskctx) throws OperatingContextException {
				callback.returnEmpty();
				taskctx.returnEmpty();
			}
		});
		
		Task task = Task.ofSubtask("Bulk feed history", "BULK")
				.withTimeout(5)		// allow some time
				.withWork(opswork);
		
		TaskHub.submit(task);
	}
}
