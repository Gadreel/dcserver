package dcraft.sql.work;

import dcraft.db.DatabaseResource;
import dcraft.hub.ResourceHub;
import dcraft.hub.config.CoreLoaderWork;
import dcraft.hub.resource.ResourceTier;
import dcraft.sql.SqlDatabaseResource;
import dcraft.task.TaskContext;
import dcraft.xml.XElement;

/**
 */
public class SqlStartWork extends CoreLoaderWork {
	@Override
	public void firstload(TaskContext taskctx, ResourceTier tier) {
		SqlDatabaseResource dbres = tier.getOrCreateTierSqlDatabases();

		for (XElement dbconfig : tier.getConfig().getTagListDeep("SqlDatabase"))
			dbres.load(dbconfig);

		taskctx.returnEmpty();
	}
	
	@Override
	public void reload(TaskContext taskctx, ResourceTier tier) {
		// requires application restart to switch databases, instead we just reuse the current resource

		SqlDatabaseResource dbres = ResourceHub.getTopResources().getSqlDatabases();		// use of RH here is deliberate

		if (dbres != null)
			tier.with(dbres);

		taskctx.returnEmpty();
	}
}
