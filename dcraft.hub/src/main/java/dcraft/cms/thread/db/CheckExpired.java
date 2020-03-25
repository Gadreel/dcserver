package dcraft.cms.thread.db;

import dcraft.cms.thread.work.CheckExpiredAll;
import dcraft.db.ICallContext;
import dcraft.db.proc.IUpdatingStoredProc;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.task.Task;
import dcraft.task.TaskContext;
import dcraft.task.TaskHub;
import dcraft.task.TaskObserver;

public class CheckExpired implements IUpdatingStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		Task task = Task.ofSubtask("Check Expired All", "DB")
				.withTimeout(10)
				.withWork(CheckExpiredAll.of(request.getInterface()));
		
		TaskHub.submit(task, new TaskObserver() {
			@Override
			public void callback(TaskContext task) {
				callback.returnEmpty();
			}
		});
	}
}
