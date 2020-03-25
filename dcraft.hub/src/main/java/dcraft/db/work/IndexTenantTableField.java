package dcraft.db.work;

import dcraft.db.BasicRequestContext;
import dcraft.db.DatabaseAdapter;
import dcraft.db.IRequestContext;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.schema.DbField;
import dcraft.schema.SchemaResource;
import dcraft.schema.TableView;
import dcraft.struct.RecordStruct;
import dcraft.task.IWork;
import dcraft.task.TaskContext;

import static dcraft.db.Constants.DB_GLOBAL_INDEX;
import static dcraft.db.Constants.DB_GLOBAL_INDEX_SUB;

public class IndexTenantTableField implements IWork {
	static public IndexTenantTableField of(DatabaseAdapter conn) {
		IndexTenantTableField tenant = new IndexTenantTableField();
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
		else if (params.isFieldEmpty("Field")) {
			Logger.error("Missing field name");
		}
		else {
			SchemaResource schemares = ResourceHub.getResources().getSchema();

			String tablename = params.getFieldAsString("Table");
			String fieldname = params.getFieldAsString("Field");

			TableView table = ResourceHub.getResources().getSchema().getTableView(tablename);
			DbField schema = schemares.getDbField(tablename, fieldname);

			if ((table == null) || (schema == null)) {
				Logger.error("Missing schema");
			}
			else {
				Logger.info("Killing indexes for: " + taskctx.getTenant().getAlias() + " table: " + tablename + " field: " + fieldname);

				try {
					if (!schema.isList() && !schema.isDynamic()) {
						this.conn.kill(tablesContext.getTenant(), DB_GLOBAL_INDEX, tablename, fieldname);
					}
					else {
						this.conn.kill(tablesContext.getTenant(), DB_GLOBAL_INDEX_SUB, tablename, fieldname);
					}

					adapter.indexCleanRecords(taskctx, table, schema);
				}
				catch (Exception x) {
					Logger.error("unable to remove indexes");
				}
			}
		}

		taskctx.returnEmpty();
	}
}
