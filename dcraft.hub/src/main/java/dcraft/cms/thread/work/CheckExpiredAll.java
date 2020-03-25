package dcraft.cms.thread.work;

import dcraft.db.DatabaseAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.UserContext;
import dcraft.task.*;
import dcraft.task.run.WorkTopic;
import dcraft.tenant.Tenant;
import dcraft.tenant.TenantHub;

import java.util.ArrayDeque;
import java.util.Queue;

public class CheckExpiredAll implements IWork {
	static public CheckExpiredAll of(DatabaseAdapter conn) {
		CheckExpiredAll tenant = new CheckExpiredAll();
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
				.withTitle("Check Expired Tenant")
				.withNextId("DB")
				.withTopic(WorkTopic.SYSTEM)
				.withTimeout(5)
				.withWork(CheckExpiredTenant.of(this.conn));
		
		TaskHub.submit(task, new TaskObserver() {
			@Override
			public void callback(TaskContext task) {
				taskctx.resume();
			}
		});
	}
}
