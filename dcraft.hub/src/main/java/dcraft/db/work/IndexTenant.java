package dcraft.db.work;

import dcraft.db.BasicRequestContext;
import dcraft.db.DatabaseAdapter;
import dcraft.db.IRequestContext;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.schema.TableView;
import dcraft.task.IWork;
import dcraft.task.TaskContext;

import static dcraft.db.Constants.DB_GLOBAL_INDEX;
import static dcraft.db.Constants.DB_GLOBAL_INDEX_SUB;

public class IndexTenant implements IWork {
	static public IndexTenant of(DatabaseAdapter conn) {
		IndexTenant tenant = new IndexTenant();
		tenant.conn = conn;
		return tenant;
	}
	
	protected DatabaseAdapter conn = null;
	
	@Override
	public void run(TaskContext taskctx) throws OperatingContextException {
		IRequestContext tablesContext = BasicRequestContext.of(this.conn);
		TablesAdapter adapter = TablesAdapter.of(tablesContext);
		
		Logger.info("Killing indexes for: " + taskctx.getTenant().getAlias());
		
		try {
			this.conn.kill(tablesContext.getTenant(), DB_GLOBAL_INDEX);
			this.conn.kill(tablesContext.getTenant(), DB_GLOBAL_INDEX_SUB);
		}
		catch (Exception x) {
			Logger.error("unable to remove indexes");
			taskctx.returnEmpty();
			return;
		}
		
		for (TableView table : ResourceHub.getResources().getSchema().getTables()) {
			Logger.info("Indexing Table: " + table.getName());
			
			/*
			boolean hasIndex = false;
			
			for (DbField field : table.fields.values()) {
				if (! field.isIndexed())
					continue;
				
				hasIndex = true;
			}
			
			if (! hasIndex)
				continue;;
			*/
			
			adapter.indexCleanRecords(taskctx, table);
			
			/*
			System.out.println("Table: " + table.getName());
			
			for (DbField fld : table.getFields()) {
				System.out.println("  - " + fld.getName());
			}
			*/
		}
		
		taskctx.returnEmpty();
	}
}
