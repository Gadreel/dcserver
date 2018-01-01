package dcraft.sql.work;

import dcraft.hub.config.CoreCleanerWork;
import dcraft.hub.resource.ResourceTier;
import dcraft.task.TaskContext;

/**
 */
public class SqlStopWork extends CoreCleanerWork {
	@Override
	public void shutdown(TaskContext taskctx, ResourceTier tier) {
		// TODO move SQL to HubResources, stop via that
		// Hub.sqldbman.stop();
        
        taskctx.returnEmpty();
	}
	
	@Override
	public void cleanup(TaskContext taskctx, ResourceTier tier) {
		taskctx.returnEmpty();
	}
}
