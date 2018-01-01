package dcraft.db.work;

import dcraft.db.DatabaseResource;
import dcraft.hub.ResourceHub;
import dcraft.hub.config.CoreLoaderWork;
import dcraft.hub.resource.ResourceTier;
import dcraft.task.TaskContext;
import dcraft.xml.XElement;

/**
 */
public class DbStartWork extends CoreLoaderWork {
	@Override
	public void firstload(TaskContext taskctx, ResourceTier tier) {
		DatabaseResource dbres = tier.getOrCreateTierDatabases();
  
		for (XElement dbconfig : tier.getConfig().getTagListDeep("Database"))
			dbres.load(dbconfig);
		
        taskctx.returnEmpty();
	}
	
	@Override
	public void reload(TaskContext taskctx, ResourceTier tier) {
		// requires application restart to switch databases, instead we just reuse the current resource
		
		DatabaseResource dbres = ResourceHub.getTopResources().getDatabases();		// use of RH here is deliberate
		
		if (dbres != null)
			tier.with(dbres);
		
		taskctx.returnEmpty();
	}
}
