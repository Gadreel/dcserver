package dcraft.task.queue;

import dcraft.hub.config.CoreLoaderWork;
import dcraft.hub.resource.ResourceTier;
import dcraft.log.Logger;
import dcraft.task.TaskContext;
import dcraft.xml.XElement;

public class StandardQueueStart extends CoreLoaderWork {
	@Override
	public void firstload(TaskContext taskctx, ResourceTier tier) {
		Logger.debugTr(0, "Initializing Task Queue");
		
		XElement config = tier.getConfig().getTag("WorkQueue");
		
		if (config == null)
			config = XElement.tag("WorkQueue");
		
		// TODO start up
		
		QueueHub.enableQueueChecker();
		
		taskctx.returnEmpty();
	}
	
	@Override
	public void reload(TaskContext taskctx, ResourceTier tier) {
		this.firstload(taskctx, tier);
	}
}
