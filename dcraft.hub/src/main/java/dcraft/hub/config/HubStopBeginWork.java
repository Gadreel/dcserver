package dcraft.hub.config;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.app.HubState;
import dcraft.hub.resource.ResourceTier;
import dcraft.log.Logger;
import dcraft.task.TaskContext;

/**
 */
public class HubStopBeginWork extends CoreCleanerWork {
	@Override
	public void shutdown(TaskContext taskctx, ResourceTier tier) {
		ApplicationHub.setState(HubState.Stopping);
		
		Logger.info("Hub entered Stopping state");
		
		Logger.boundary("Origin", "hub:", "Op", "Stop");
		
		Logger.info(0, "Stopping hub");
        
        taskctx.returnEmpty();
	}
	
	@Override
	public void cleanup(TaskContext taskctx, ResourceTier tier) {
		taskctx.returnEmpty();
	}
}
