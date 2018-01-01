package dcraft.sql.work;

import dcraft.hub.config.CoreLoaderWork;
import dcraft.hub.resource.ResourceTier;
import dcraft.task.TaskContext;

/**
 */
public class SqlStartWork extends CoreLoaderWork {
	@Override
	public void firstload(TaskContext taskctx, ResourceTier tier) {
		// TODO move SQL to ResourceHub, init via that
        
        taskctx.returnEmpty();
	}
	
	@Override
	public void reload(TaskContext taskctx, ResourceTier tier) {
		// requires application restart to switch databases
		
		// TODO copy sql resources from current top resources, like this
		//tier.with(ResourceHub.getTopResources().getDatabases());
		
		taskctx.returnEmpty();
	}
}
