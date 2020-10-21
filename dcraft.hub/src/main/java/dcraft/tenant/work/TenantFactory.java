package dcraft.tenant.work;

import java.util.List;

import dcraft.hub.op.OperationContext;
import dcraft.hub.op.UserContext;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.task.Task;
import dcraft.task.TaskContext;
import dcraft.task.run.WorkTopic;
import dcraft.tenant.Tenant;

public class TenantFactory {
	/*
	 * use the _System topic so that there will only every be one Update running at a given time
	 * this is instead of locking - we can handle multiple independent updates in the order they
	 * arrive because each update job runs from the queue - one at a time.
	 */
	
	static public Task updateTenants(boolean update, List<Tenant> tenants, List<String> removes, boolean reloadmode) {
		UpdateWork work = new UpdateWork();
		
		work.setUpdate(update);
		work.setTenants(tenants);
		work.setRemoves(removes);
		work.setSkipSchedule(reloadmode);
		
		Task task = Task.ofHubRoot()
			.withNextId("UpdateTenants")
			.withTitle("Update Tenants")
			.withTopic(WorkTopic.SYSTEM)
			.withWork(work);

		return task;
	}
	
	// only use this inside UpdateWork 
	static public Task prepareTenant(TaskContext trun, Tenant tenant) {
		PrepWork work = new PrepWork();
		
		work.setTenant(tenant);
		
		// use a clone of the current context, except it run in a different tenant (root site)
		TaskContext tctx = trun.deepCopy();
		
		tctx.getFieldAsRecord("User")
				.with("Tenant", tenant.getAlias());

		// keep original controller
		tctx.with("Controller", trun.getController());

		// no Topic because we would be blocked by UpdateWork already running
		Task task = Task.ofContext(tctx)
				.withNextId("PrepWork")
				//.withController(trun.getController())					// keep same controller for keep alive
				.withTitle("Prepare Tenant: " + tenant.getAlias())
				.withWork(work);
		
		return task;
	}
	
	// only use this inside UpdateWork 
	static public Task cleanTenant(TaskContext trun, Tenant tenant) {
		CleanupWork work = new CleanupWork();
		
		work.setTenant(tenant);
		
		// use a clone of the current context, except it run in a different tenant (root site)
		TaskContext tctx = trun.deepCopy();
		
		tctx.getFieldAsRecord("User")
				.with("Tenant", tenant.getAlias());

		// keep original controller
		tctx.with("Controller", trun.getController());
				
		// no Topic because we would be blocked by UpdateWork already running
		Task task = Task.ofContext(tctx)
				.withNextId("CleanWork")
				//.withController(trun.getController())					// keep same controller for keep alive
				.withTitle("Cleanup Old Tenant: " + tenant.getAlias())
				.withWork(work);

		return task;
	}
}
