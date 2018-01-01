package dcraft.task.scheduler;

import dcraft.hub.clock.ISystemWork;
import dcraft.hub.clock.SysReporter;

public class ScheduleExecuteSysWorker implements ISystemWork {
	@Override
	public void run(SysReporter reporter) {
		// remember that sys workers should not use OperationContext
		ScheduleHub.execute();
	}
	
	@Override
	public int period() {
		return 1;
	}
}
