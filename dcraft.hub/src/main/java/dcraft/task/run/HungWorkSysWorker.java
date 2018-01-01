package dcraft.task.run;

import dcraft.hub.clock.ISystemWork;
import dcraft.hub.clock.SysReporter;

public class HungWorkSysWorker implements ISystemWork {
	// TODO remember that sys workers should not use OperationContext
	// the task defines a timeout, not the pool.  tasks with no timeout set
	// will simply not timeout and the pool will be burdened - so set timeouts
	// on tasks if there is any possibility that they might
	@Override
	public void run(SysReporter reporter) {
		reporter.setStatus("Reviewing hung topics");
		
		// even when stopping we still want to clear hung tasks
		for (int i = 0; i < WorkHub.slots.length; i++) {
			Worker w = WorkHub.slots[i];
			
			if (w != null)
				w.checkIfHung();
		}
		
		for (WorkTopic b : WorkHub.topics.values())
			b.checkIfHung();
		
		reporter.setStatus("After reviewing hung topics");
	}
	
	@Override
	public int period() {
		return 5;		// TODO configure
	}
}
