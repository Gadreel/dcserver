package dcraft.hub.config;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.app.HubState;
import dcraft.hub.resource.ResourceTier;
import dcraft.log.HubLog;
import dcraft.log.Logger;
import dcraft.task.TaskContext;
import io.netty.channel.EventLoopGroup;

/**
 */
public class HubStopFinalWork extends CoreCleanerWork {
	@Override
	public void shutdown(TaskContext taskctx, ResourceTier tier) {
		/*

		*/
		Logger.debug(0, "Stopping IP threads");
		
		try {
			EventLoopGroup grp = ApplicationHub.getEventLoopGroupIfPresent();
			
			if (grp != null)
				grp.shutdownGracefully().await();
		}
		catch (InterruptedException x) {
		}
		
		Logger.debug( "Stopping logger");
		HubLog.stop();
		
		Logger.info( "Hub stopped");
		
		ApplicationHub.setState(HubState.Stopped);
        
        taskctx.returnEmpty();
	}
	
	@Override
	public void cleanup(TaskContext taskctx, ResourceTier tier) {
		taskctx.returnEmpty();
	}
}
