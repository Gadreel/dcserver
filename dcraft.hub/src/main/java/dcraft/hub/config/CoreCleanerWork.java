package dcraft.hub.config;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.resource.ResourceTier;
import dcraft.task.IWork;
import dcraft.task.TaskContext;

/**
 */
abstract public class CoreCleanerWork implements IWork {
	@Override
	public void run(TaskContext taskctx) throws OperatingContextException {
		ResourceTier tier = (ResourceTier) taskctx.getTask().getParamsAsRecord().getFieldAsComposite("OldTier");
		
		// if new tier is present this is just a cleanup
		if (taskctx.getTask().getParamsAsRecord().hasField("Tier"))
			this.cleanup(taskctx, tier);
		else
			this.shutdown(taskctx, tier);
	}
	
	abstract public void shutdown(TaskContext taskctx, ResourceTier tier);
	
	abstract public void cleanup(TaskContext taskctx, ResourceTier tier);
}
