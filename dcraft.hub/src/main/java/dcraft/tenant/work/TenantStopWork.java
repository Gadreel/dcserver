package dcraft.tenant.work;

import dcraft.hub.config.CoreCleanerWork;
import dcraft.hub.resource.ResourceTier;
import dcraft.task.TaskContext;
import dcraft.tenant.TenantHub;

/**
 */
public class TenantStopWork extends CoreCleanerWork {
	
	@Override
	public void shutdown(TaskContext taskctx, ResourceTier tier) {
		TenantHub.stopFileQueue();
        
        taskctx.returnEmpty();
	}
	
	@Override
	public void cleanup(TaskContext taskctx, ResourceTier tier) {
		taskctx.returnEmpty();
	}
}
