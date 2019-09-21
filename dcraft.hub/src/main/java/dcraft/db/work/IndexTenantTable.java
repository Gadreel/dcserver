package dcraft.db.work;

import dcraft.db.BasicRequestContext;
import dcraft.db.DatabaseAdapter;
import dcraft.db.IRequestContext;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.schema.TableView;
import dcraft.struct.RecordStruct;
import dcraft.task.IWork;
import dcraft.task.TaskContext;

import static dcraft.db.Constants.DB_GLOBAL_INDEX;
import static dcraft.db.Constants.DB_GLOBAL_INDEX_SUB;

public class IndexTenantTable implements IWork {
	static public IndexTenantTable of(DatabaseAdapter conn) {
		IndexTenantTable tenant = new IndexTenantTable();
		tenant.conn = conn;
		return tenant;
	}
	
	protected DatabaseAdapter conn = null;
	
	@Override
	public void run(TaskContext taskctx) throws OperatingContextException {
		IRequestContext tablesContext = BasicRequestContext.of(this.conn);
		TablesAdapter adapter = TablesAdapter.ofNow(tablesContext);

		RecordStruct params = taskctx.getTask().getParamsAsRecord();

		if ((params == null) || params.isFieldEmpty("Table")) {
			Logger.error("Missing table name");
		}
		else {
			String tablename = params.getFieldAsString("Table");

			Logger.info("Killing indexes for: " + taskctx.getTenant().getAlias() + " table: " + tablename);

			try {
				this.conn.kill(tablesContext.getTenant(), DB_GLOBAL_INDEX, tablename);
				this.conn.kill(tablesContext.getTenant(), DB_GLOBAL_INDEX_SUB, tablename);
			}
			catch (Exception x) {
				Logger.error("unable to remove indexes");
				taskctx.returnEmpty();
				return;
			}

			TableView table = ResourceHub.getResources().getSchema().getTableView(tablename);
			Logger.info("Indexing Table: " + table.getName());

			adapter.indexCleanRecords(taskctx, table);
		}

		taskctx.returnEmpty();
	}
}
