package dcraft.service.work;

import dcraft.hub.config.CoreCleanerWork;
import dcraft.hub.resource.ResourceTier;
import dcraft.service.ServiceResource;
import dcraft.task.TaskContext;

/**
 */
public class ServiceStopWork extends CoreCleanerWork {
	@Override
	public void shutdown(TaskContext taskctx, ResourceTier tier) {
		ServiceResource srres = tier.getOrCreateTierServices();
		
		srres.cleanup();
		
		taskctx.returnEmpty();
	}
	
	@Override
	public void cleanup(TaskContext taskctx, ResourceTier tier) {
		this.shutdown(taskctx, tier);
	}
}
