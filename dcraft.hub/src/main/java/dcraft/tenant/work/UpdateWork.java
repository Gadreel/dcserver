package dcraft.tenant.work;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.app.HubEvents;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.UserContext;
import dcraft.log.Logger;
import dcraft.task.TaskContext;
import dcraft.task.TaskHub;
import dcraft.task.TaskObserver;
import dcraft.task.scheduler.ScheduleHub;
import dcraft.task.scheduler.common.CommonSchedule;
import dcraft.tenant.Tenant;
import dcraft.tenant.TenantHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.task.StateWork;
import dcraft.task.StateWorkStep;
import dcraft.task.Task;
import dcraft.xml.XElement;

public class UpdateWork extends StateWork {
	protected boolean update = false;
	protected List<Tenant> tenants = null;
	protected List<String> removes = null;
	protected List<Tenant> cleantenants = null;
	
	protected int currentupdate = 0;
	protected int currentclean = 0;
	
	public void setUpdate(boolean v) {
		this.update = v;
	}
	
	public void setTenants(List<Tenant> v) {
		this.tenants = v;
	}
	
	public void setRemoves(List<String> v) {
		this.removes = v;
	}

	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this
			.withStep(StateWorkStep.of("Prepare Tenants", this::prepTenant))
			.withStep(StateWorkStep.of("Publish Tenant Changes", this::publish))
			.withStep(StateWorkStep.of("Cleanup Tenants", this::cleanTenant))
			.withStep(StateWorkStep.of("Prep Schedules", this::schedules))
			.withStep(StateWorkStep.of("Tenants Events", this::eventTenant));
	}
	
	public StateWorkStep prepTenant(TaskContext trun) throws OperatingContextException {
		if ((this.tenants == null) || (this.tenants.size() <= this.currentupdate))
			return StateWorkStep.NEXT;
		
		Tenant ten = this.tenants.get(this.currentupdate);
		
		Task task = TenantFactory.prepareTenant(trun, ten);
		
		// TODO -- won't keep alive, not same controller
		
		TaskHub.submit(task, new TaskObserver() {
			@Override
			public void callback(TaskContext task) {
				if (task.hasExitErrors()) {
					trun.setExitCode(task.getExitCode(), "Prep tenant failed.");
					trun.kill("Unable to update tenants: " + task.getExitMessage());
					return;
				}
				
				UpdateWork.this.currentupdate++;
				trun.resume();
			}
		});
		
		return StateWorkStep.WAIT;
	}
	
	public StateWorkStep publish(TaskContext trun) throws OperatingContextException {

		/* TODO
		if (removes != null) {
			for (String alias : removes) {
				newaliasmap.remove(alias);
			}
		}
		*/

		// TODO come up with a list for cleantenants
		
		if (this.update) {
			Collection<Tenant> newtenants = new ArrayList<>();
	
			for (Tenant ten : TenantHub.getTenants()) {
				boolean fnd = false;
				
				for (Tenant upten : this.tenants) {
					if (ten.getAlias().equals(upten.getAlias())) {
						newtenants.add(upten);
						fnd = true;
						break;
					}
				}
				
				if (! fnd)
					newtenants.add(ten);
			}
			
			TenantHub.internalSwitchTenants(newtenants);
		}
		else {
			this.cleantenants = new ArrayList<>(TenantHub.getTenants());
			
			TenantHub.internalSwitchTenants(this.tenants);
		}
		
		return StateWorkStep.NEXT;
	}
	
	public StateWorkStep cleanTenant(TaskContext trun) throws OperatingContextException {
		if ((this.cleantenants == null) || (this.cleantenants.size() <= this.currentclean))
			return StateWorkStep.NEXT;
		
		Tenant ten = this.cleantenants.get(this.currentclean);
		
		Task task = TenantFactory.cleanTenant(trun, ten);
		
		// will keep alive, same controller
		
		TaskHub.submit(task, new TaskObserver() {
			@Override
			public void callback(TaskContext task) {
				// doesn't matter if there was an error, keep trying to clean - we have committed to switch
				
				UpdateWork.this.currentclean++;
				trun.resume();
			}
		});
		
		return StateWorkStep.WAIT;
	}
	
	public StateWorkStep schedules(TaskContext trun) throws OperatingContextException {
		if (Logger.isDebug())
			Logger.debug("Starting tenant load schedule for node");
		
		// just the root
		List<XElement> schedules = ResourceHub.getResources().getConfig().getTagListDeep("Schedules/*");
		
		for (XElement schedule : schedules) {
			String sfor = schedule.getAttribute("For", "Production,Test");
			
			if (! ApplicationHub.isProduction() && ! sfor.contains("Test"))
				continue;
			
			if (ApplicationHub.isProduction() && ! sfor.contains("Production"))
				continue;
			
			Logger.info("- add schedule: " + schedule.getAttribute("Title"));
			
			if ("CommonSchedule".equals(schedule.getName())) {
				UserContext userContext = UserContext.rootUser(schedule.getAttribute("Tenant", "root"),
						schedule.getAttribute("Site", "root"));

				Task schcontext = Task.of(OperationContext.context(userContext))
						.withTitle("Scheduled run: " + schedule.getAttribute("Title"))
						.withTopic("Batch")		// TODO need to build batch into the system
						.withNextId("SCHEDULE");
				
				CommonSchedule sched = CommonSchedule.of(schcontext.freezeToRecord());
				
				sched.init(schedule);
				
				// TODO may not need this
				// sched.setTenantId(this.getId());
				
				Logger.info("- prepped schedule: " + schedule.getAttribute("Title") + " next run " + Instant.ofEpochMilli(sched.when()));
				
				// TODO record these for cancel and reload
				// this.schedulenodes.add(sched);
				
				ScheduleHub.addNode(sched);
			}
			else {
				Logger.error("- could not prep schedule: " + schedule.getAttribute("Title") + " not a common schedule");
			}
		}
		
		return StateWorkStep.NEXT;
	}
	
	public StateWorkStep eventTenant(TaskContext trun) throws OperatingContextException {
		ApplicationHub.fireEvent(HubEvents.TenantsReloaded, null);
		
		return StateWorkStep.NEXT;
	}
}
