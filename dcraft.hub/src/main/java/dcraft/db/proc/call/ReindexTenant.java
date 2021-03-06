package dcraft.db.proc.call;

import dcraft.db.proc.IUpdatingStoredProc;
import dcraft.db.ICallContext;
import dcraft.db.work.IndexTenant;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.task.Task;
import dcraft.task.TaskContext;
import dcraft.task.TaskHub;
import dcraft.task.TaskObserver;
import dcraft.task.run.WorkTopic;

public class ReindexTenant implements IUpdatingStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		Task task = Task.ofSubtask("ReIndex Tenant", "DB")
				.withTopic(WorkTopic.SYSTEM)
				.withTimeout(5)
				.withWork(IndexTenant.of(request.getInterface()));
		
		TaskHub.submit(task, new TaskObserver() {
			@Override
			public void callback(TaskContext task) {
				callback.returnEmpty();
			}
		});
	}
}
