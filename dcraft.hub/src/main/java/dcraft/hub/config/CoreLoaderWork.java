package dcraft.hub.config;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.resource.ResourceTier;
import dcraft.log.Logger;
import dcraft.task.IWork;
import dcraft.task.TaskContext;

/**
 */
abstract public class CoreLoaderWork implements IWork {
	@Override
	public void run(TaskContext taskctx) throws OperatingContextException {
		ResourceTier tier = (ResourceTier) taskctx.getTask().getParamsAsRecord().getFieldAsComposite("Tier");
		
		Logger.trace("Start core loader work: " + this.getClass().getCanonicalName());
		
		if (ApplicationHub.isOperational())
			this.reload(taskctx, tier);
		else
			this.firstload(taskctx, tier);
	}
	
	abstract public void firstload(TaskContext taskctx, ResourceTier tier);
	
	abstract public void reload(TaskContext taskctx, ResourceTier tier);
}
