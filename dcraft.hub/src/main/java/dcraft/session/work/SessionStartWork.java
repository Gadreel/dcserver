package dcraft.session.work;

import dcraft.hub.config.CoreLoaderWork;
import dcraft.hub.resource.ResourceTier;
import dcraft.session.SessionHub;
import dcraft.task.TaskContext;

/**
 */
public class SessionStartWork extends CoreLoaderWork {
	@Override
	public void firstload(TaskContext taskctx, ResourceTier tier) {
		SessionHub.loadCleanup();
	
        taskctx.returnEmpty();
	}
	
	@Override
	public void reload(TaskContext taskctx, ResourceTier tier) {
		taskctx.returnEmpty();
	}
}
