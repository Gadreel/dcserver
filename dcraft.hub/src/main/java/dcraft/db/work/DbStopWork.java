package dcraft.db.work;

import dcraft.db.DatabaseResource;
import dcraft.hub.config.CoreCleanerWork;
import dcraft.hub.resource.ResourceTier;
import dcraft.task.TaskContext;

/**
 */
public class DbStopWork extends CoreCleanerWork {
	@Override
	public void shutdown(TaskContext taskctx, ResourceTier tier) {
		DatabaseResource dbres = tier.getOrCreateTierDatabases();
  
		if (dbres != null)
			dbres.cleanup();
        
        taskctx.returnEmpty();
	}
	
	@Override
	public void cleanup(TaskContext taskctx, ResourceTier tier) {
		taskctx.returnEmpty();
	}
}
