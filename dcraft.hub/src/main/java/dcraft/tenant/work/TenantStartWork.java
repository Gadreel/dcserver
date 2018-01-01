package dcraft.tenant.work;

import dcraft.filestore.local.LocalStore;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.config.CoreLoaderWork;
import dcraft.hub.resource.ConfigResource;
import dcraft.hub.resource.ResourceTier;
import dcraft.log.Logger;
import dcraft.task.TaskContext;
import dcraft.tenant.TenantHub;
import dcraft.xml.XElement;

/**
 */
public class TenantStartWork extends CoreLoaderWork {
	@Override
	public void firstload(TaskContext taskctx, ResourceTier tier) {
		// config doesn't seem to improve anything
		//ConfigResource config = tier.getConfig();

		//XElement fstore = config.getTag("TenantsFileStore");
		
		//if (fstore == null)
		//	fstore = XElement.tag("TenantsFileStore");
	
		Logger.debug( "Initializing tenants file store");
		
		//TenantHub.setFileStore(LocalStore.of(fstore.getAttribute("FolderPath", "./deploy-" + ApplicationHub.getDeployment() + "/tenants")));
		TenantHub.setFileStore(LocalStore.of(ApplicationHub.getDeploymentTenantsPath()));
		
		Logger.debug( "Loading tenants file queue");
		
		TenantHub.loadFileQueue();
		
		TenantHub.loadDeafultTenant();
		
        taskctx.returnEmpty();
	}
	
	@Override
	public void reload(TaskContext taskctx, ResourceTier tier) {
		// for changes to the TenantHub, an application restart will be required
		
		taskctx.returnEmpty();
	}
}
