package dcraft.task.work;

import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.task.*;
import dcraft.task.queue.StandardQueueStart;
import dcraft.task.run.StandardWorkStart;
import dcraft.task.scheduler.StandardSchedulerStart;

public class StandardTaskStart extends StateWork {
	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this
				.withStep(StateWorkStep.of("Prepare Task Hub", this::prepTask))
				.withStep(StateWorkStep.of("Start Work Hub", this, new StandardWorkStart()))
				.withStep(StateWorkStep.of("Start Scheduler Hub", this, new StandardSchedulerStart()))
				.withStep(StateWorkStep.of("Start Queue Hub", this, new StandardQueueStart()))
				.withStep(StateWorkStep.of("Start Task Hub", this::startTask));
	}
	
	public StateWorkStep prepTask(TaskContext trun) throws OperatingContextException {
		Logger.debug("Starting Task Hub");
		
		return StateWorkStep.NEXT;
	}
	
	public StateWorkStep startTask(TaskContext trun) throws OperatingContextException {
		Logger.debug("Started Task Hub");
		
		return StateWorkStep.NEXT;
	}
}
