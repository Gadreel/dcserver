package dcraft.tool.backup;

import dcraft.cms.thread.work.CheckExpiredTenant;
import dcraft.db.BasicRequestContext;
import dcraft.db.DatabaseAdapter;
import dcraft.db.IRequestContext;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.UserContext;
import dcraft.log.Logger;
import dcraft.schema.TableView;
import dcraft.task.ChainWork;
import dcraft.task.IWork;
import dcraft.task.Task;
import dcraft.task.TaskContext;
import dcraft.task.run.WorkTopic;

import static dcraft.db.Constants.DB_GLOBAL_INDEX;
import static dcraft.db.Constants.DB_GLOBAL_INDEX_SUB;

public class DailyForTenant extends ChainWork {
	static public DailyForTenant of(DatabaseAdapter conn) {
		DailyForTenant tenant = new DailyForTenant();
		tenant.conn = conn;
		return tenant;
	}
	
	protected DatabaseAdapter conn = null;

	@Override
	protected void init(TaskContext taskctx) {
		Logger.info("Start nightly BATCH");

		this.then(CheckExpiredTenant.of(this.conn));
	}
}
