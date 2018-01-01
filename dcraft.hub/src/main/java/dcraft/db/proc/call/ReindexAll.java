package dcraft.db.proc.call;

import dcraft.db.DbServiceRequest;
import dcraft.db.proc.IUpdatingStoredProc;
import dcraft.db.work.IndexAll;
import dcraft.db.work.IndexTenant;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.task.Task;
import dcraft.task.TaskContext;
import dcraft.task.TaskHub;
import dcraft.task.TaskObserver;
import dcraft.task.run.WorkTopic;
import dcraft.util.cb.TaskCountDownCallback;

public class ReindexAll implements IUpdatingStoredProc {
	@Override
	public void execute(DbServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException {
		Task task = Task.ofSubtask("ReIndex All", "DB")
				.withTimeout(10)
				.withWork(IndexAll.of(request.getInterface()));
		
		TaskHub.submit(task, new TaskObserver() {
			@Override
			public void callback(TaskContext task) {
				callback.returnEmpty();
			}
		});
	}
}
