package dcraft.tool.backup;

import dcraft.db.BasicRequestContext;
import dcraft.db.DatabaseAdapter;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.UserContext;
import dcraft.task.*;
import dcraft.task.run.WorkTopic;
import dcraft.tenant.Tenant;
import dcraft.tenant.TenantHub;

import java.util.ArrayDeque;
import java.util.Queue;

public class DailyAllTenants implements IWork {
	static public DailyAllTenants of(DatabaseAdapter conn) {
		DailyAllTenants tenant = new DailyAllTenants();
		tenant.conn = conn;
		return tenant;
	}
	
	protected DatabaseAdapter conn = null;
	protected Queue<String> tenants = null;
	
	@Override
	public void run(TaskContext taskctx) throws OperatingContextException {
		if (this.conn == null) {
			BasicRequestContext requestContext = BasicRequestContext.ofDefaultDatabase();
			this.conn = requestContext.getInterface();
		}

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
				.withTitle("Daily for Tenant")
				.withNextId("BATCH")
				.withTopic(WorkTopic.SYSTEM)
				.withTimeout(taskctx.getTask().getTimeout())
				.withWork(DailyForTenant.of(this.conn));
		
		TaskHub.submit(task, new TaskObserver() {
			@Override
			public void callback(TaskContext task) {
				taskctx.resume();
			}
		});
	}
}
