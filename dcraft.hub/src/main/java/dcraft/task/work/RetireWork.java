package dcraft.task.work;

import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.task.IWork;
import dcraft.task.TaskContext;

public class RetireWork implements IWork {
	@Override
	public void run(TaskContext taskctx) throws OperatingContextException {
		//Logger.info("Retire run on thread: " + Thread.currentThread().getName());
		
		// nothing to do, this task is run only so that workers can
		taskctx.returnEmpty();
	}
}
