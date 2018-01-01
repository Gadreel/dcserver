package dcraft.script.work;

import dcraft.hub.config.CoreCleanerWork;
import dcraft.hub.resource.ResourceTier;
import dcraft.task.TaskContext;

/**
 */
public class ScriptStopWork extends CoreCleanerWork {
	@Override
	public void shutdown(TaskContext taskctx, ResourceTier tier) {
		// nothing for now
		
		taskctx.returnEmpty();
	}
	
	@Override
	public void cleanup(TaskContext taskctx, ResourceTier tier) {
		taskctx.returnEmpty();
	}
}
