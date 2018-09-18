package dcraft.task.run;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.task.*;
import dcraft.task.StateWork;
import dcraft.task.StateWorkStep;

import java.util.List;

public class StandardWorkStop extends StateWork {
	protected List<Worker> oldworkers = null;
	protected int waitTries = 0;
	protected int cleanTries = 0;
	
	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this
				.withStep(StateWorkStep.of("Wait on remaining tasks", this::waitTask));
				//.withStep(StateWorkStep.allocate("Switch to One Thread", this::prepTask))
				//.withStep(StateWorkStep.allocate("Cleanup Old Threads", this::cleanup));
	}
	
	// current task, having just resumed above, will no longer be in the oldworkers list
	// everything in that list needs to be waited on
	public StateWorkStep waitTask(TaskContext trun) throws OperatingContextException {
		// if this is just a cleanup then don't do the waits
		if (ApplicationHub.isOperational())
			return StateWorkStep.NEXT;
		
		if (this.waitTries >= 1000)		// 4 minutes, 10 seconds
			return StateWorkStep.NEXT;
		
		Logger.debug("Checking running in Work Hub");
		
		int busycnt = 0;
		
		for (Worker w : WorkHub.slots) {
			if (w.isBusy()) {
				busycnt++;
				
				//Logger.info("Waiting on Work Hub - found busy: " + w);
			}
		}
		
		if (busycnt < 2)
			return StateWorkStep.NEXT;
		
		// try waiting, only if there are more than 1 tasks because 1 = this task
		this.waitTries++;
		
		//Logger.info("Waiting on Work Hub - found busy: " + busycnt);
		
		try {
			Thread.sleep(250);
		}
		catch (InterruptedException x) {
			// its ok, just go on
		}
		
		return StateWorkStep.REPEAT;
	}
	
	/* nice examples of how to clear out the slots and free the threads, but not necessary in shutdown
	public StateWorkStep prepTask(TaskContext trun) throws OperatingContextException {
		Logger.debugTr(0, "Stopping Work Hub");
		
		// switch to one thread by first removing all the old threads
		this.oldworkers = WorkHub.resizeSlots(0);
		
		// then set in 1 new one just in case
		WorkHub.resizeSlots(1);
		
		return StateWorkStep.NEXT;
	}
	
	// current task, having just resumed above, will no longer be in the oldworkers list
	// everything in that list needs to be waited on
	public StateWorkStep cleanup(TaskContext trun) throws OperatingContextException {
		if (this.cleanTries >= 120)		// 12 seconds
			return StateWorkStep.NEXT;
		
		Logger.debugTr(0, "Checking old threads in Work Hub");
		
		int alivecnt = 0;
		
		//Logger.info("Start cleanup loop: " + trun.getSlot() + " - " + WorkHub.slots[trun.getSlot()] );
		
		for (Worker w : this.oldworkers) {
			//Logger.info("Worker review : " + w.toString());
			
			if (w.checkAlive())
				alivecnt++;
		}
		
		if (alivecnt == 0)
			return StateWorkStep.NEXT;
		
		// try waiting
		this.cleanTries++;
		
		//Logger.info("Waiting on Work Hub - found alive: " + alivecnt);
		
		try {
			Thread.sleep(100);
		}
		catch (InterruptedException x) {
			// its ok, just go on
		}
		
		CountDownCallback cdcb = new CountDownCallback(alivecnt, new OperationOutcomeEmpty() {
			@Override
			public void callback() throws OperatingContextException {
				trun.resume();
			}
		});
		
		// now flood the queue with RetireWork to get the slots to cleanup
		
		for (int i = 0; i < alivecnt; i++) {
			TaskHub.submit(Task.ofHubRoot()
							.withWork(new RetireWork()),
					new TaskObserver() {
						@Override
						public void callback(TaskContext task) {
							cdcb.countDown();
						}
					}
			);
		}
		
		return StateWorkStep.WAIT;
	}
	*/
}
