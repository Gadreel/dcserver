package dcraft.db.work;

import dcraft.db.DatabaseAdapter;
import dcraft.db.tables.BasicTablesContext;
import dcraft.db.tables.ITablesContext;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.UserContext;
import dcraft.log.Logger;
import dcraft.schema.DbTable;
import dcraft.task.IWork;
import dcraft.task.Task;
import dcraft.task.TaskContext;
import dcraft.task.TaskHub;
import dcraft.task.TaskObserver;
import dcraft.task.run.WorkTopic;
import dcraft.tenant.Tenant;
import dcraft.tenant.TenantHub;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static dcraft.db.Constants.DB_GLOBAL_INDEX;
import static dcraft.db.Constants.DB_GLOBAL_INDEX_SUB;

public class IndexAll implements IWork {
	static public IndexAll of(DatabaseAdapter conn) {
		IndexAll tenant = new IndexAll();
		tenant.conn = conn;
		return tenant;
	}
	
	protected DatabaseAdapter conn = null;
	protected Queue<String> tenants = null;
	
	@Override
	public void run(TaskContext taskctx) throws OperatingContextException {
		if (tenants == null) {
			tenants = new ArrayDeque<>();
			
			for (Tenant tenant : TenantHub.getTenants())
				tenants.add(tenant.getAlias());
		}
		
		String tenant = tenants.poll();
		
		if (tenant == null) {
			taskctx.returnEmpty();
			return;
		}
		
		Task task = Task.of(OperationContext.context(UserContext.rootUser(tenant,"root"), taskctx.getController()))
				.withTitle("ReIndex Tenant")
				.withNextId("DB")
				.withTopic(WorkTopic.SYSTEM)
				.withTimeout(5)
				.withWork(IndexTenant.of(this.conn));
		
		TaskHub.submit(task, new TaskObserver() {
			@Override
			public void callback(TaskContext task) {
				taskctx.resume();
			}
		});
	}
}
