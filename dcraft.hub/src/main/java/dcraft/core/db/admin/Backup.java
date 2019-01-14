package dcraft.core.db.admin;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.task.Task;
import dcraft.task.TaskHub;
import dcraft.tool.backup.BackupWork;

public class Backup implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		TaskHub.submit(Task.ofSubContext()
				.withTitle("Run nightly backup task")
				.withWork(new BackupWork())
		);
		
		// don't want on backup
		callback.returnEmpty();
	}
}
