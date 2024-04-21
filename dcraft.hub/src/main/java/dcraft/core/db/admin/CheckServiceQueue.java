package dcraft.core.db.admin;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.service.work.FileQueuePollWork;
import dcraft.task.Task;
import dcraft.task.TaskHub;
import dcraft.task.run.WorkTopic;
import dcraft.tool.backup.BackupWork;

public class CheckServiceQueue implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		Task peroidicChecker = Task.ofHubRoot()
				.withTitle("Review local service (msg) queue")
				.withTopic(WorkTopic.SYSTEM)
				.withNextId("QUEUE")
				.withWork(FileQueuePollWork.ofAll());

		TaskHub.submit(peroidicChecker);

		// don't want on messages
		callback.returnEmpty();
	}
}
