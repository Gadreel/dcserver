package dcraft.sql.work;

import dcraft.db.DatabaseResource;
import dcraft.hub.config.CoreCleanerWork;
import dcraft.hub.resource.ResourceTier;
import dcraft.sql.SqlDatabaseResource;
import dcraft.task.TaskContext;

/**
 */
public class SqlStopWork extends CoreCleanerWork {
	@Override
	public void shutdown(TaskContext taskctx, ResourceTier tier) {
		SqlDatabaseResource dbres = tier.getOrCreateTierSqlDatabases();

		if (dbres != null)
			dbres.cleanup();

		taskctx.returnEmpty();
	}
	
	@Override
	public void cleanup(TaskContext taskctx, ResourceTier tier) {
		taskctx.returnEmpty();
	}
}
