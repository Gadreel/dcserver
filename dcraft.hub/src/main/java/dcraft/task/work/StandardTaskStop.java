package dcraft.task.work;

import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.task.*;
import dcraft.task.queue.StandardQueueStop;
import dcraft.task.run.StandardWorkStop;
import dcraft.task.scheduler.StandardSchedulerStop;

public class StandardTaskStop extends StateWork {
	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this
				.withStep(StateWorkStep.of("Prepare Task Hub", this::prepTask))
				.withStep(StateWorkStep.of("Stop Scheduler Hub", this, new StandardSchedulerStop()))
				.withStep(StateWorkStep.of("Stop Queue Hub", this, new StandardQueueStop()))
				.withStep(StateWorkStep.of("Stop Work Hub", this, new StandardWorkStop()))
				.withStep(StateWorkStep.of("Stop Task Hub", this::stopTask));
	}
	
	public StateWorkStep prepTask(TaskContext trun) throws OperatingContextException {
		Logger.debug("Stopping Task Hub");
		
		return StateWorkStep.NEXT;
	}
	
	public StateWorkStep stopTask(TaskContext trun) throws OperatingContextException {
		Logger.debug("Stopped Task Hub");
		
		return StateWorkStep.NEXT;
	}
}
