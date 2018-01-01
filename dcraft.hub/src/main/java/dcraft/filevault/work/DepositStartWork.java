package dcraft.filevault.work;

import dcraft.filevault.DepositHub;
import dcraft.hub.config.CoreLoaderWork;
import dcraft.hub.resource.ResourceTier;
import dcraft.session.SessionHub;
import dcraft.task.TaskContext;
import dcraft.xml.XElement;

/**
 */
public class DepositStartWork extends CoreLoaderWork {
	@Override
	public void firstload(TaskContext taskctx, ResourceTier tier) {
		XElement del = tier.getConfig().getTag("Deposits");

		if (del != null) {
			DepositHub.loadTracking();
			DepositHub.enableQueueChecker();

			if (del.getAttributeAsBooleanOrFalse("SyncRemote"))
				DepositHub.enableRemoteChecker();
		}

        taskctx.returnEmpty();
	}
	
	@Override
	public void reload(TaskContext taskctx, ResourceTier tier) {
		taskctx.returnEmpty();
	}
}
