package dcraft.cms.feed.db;

import dcraft.cms.feed.work.ReindexFeedWork;
import dcraft.db.BasicRequestContext;
import dcraft.db.ICallContext;
import dcraft.db.IConnectionManager;
import dcraft.db.fileindex.FileIndexAdapter;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.filestore.CommonPath;
import dcraft.filevault.Vault;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.service.ServiceHub;
import dcraft.service.ServiceRequest;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.Task;
import dcraft.task.TaskContext;
import dcraft.task.TaskHub;
import dcraft.task.TaskObserver;

public class Reindex implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		String feed = request.getDataAsRecord().getFieldAsString("Feed");

		TaskHub.submit(Task.ofSubContext()
						.withTitle("Reindex feed: " + feed)
						.withWork(ReindexFeedWork.work(feed, request)),
				new TaskObserver() {
					@Override
					public void callback(TaskContext task) {
						callback.returnEmpty();
					}
				}
		);
	}
}
