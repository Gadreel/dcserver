package dcraft.task.scheduler;

import dcraft.hub.config.CoreCleanerWork;
import dcraft.hub.resource.ResourceTier;
import dcraft.log.Logger;
import dcraft.task.TaskContext;

public class StandardSchedulerStop extends CoreCleanerWork {
	@Override
	public void shutdown(TaskContext taskctx, ResourceTier tier) {
		Logger.debugTr(0, "Stopping Task Scheduler");
		
		ScheduleHub.active = false;
		
		taskctx.returnEmpty();
	}
	
	@Override
	public void cleanup(TaskContext taskctx, ResourceTier tier) {
		taskctx.returnEmpty();
	}
}
