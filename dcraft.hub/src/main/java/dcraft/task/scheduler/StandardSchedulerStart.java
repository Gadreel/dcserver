package dcraft.task.scheduler;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.app.HubEvents;
import dcraft.hub.app.HubState;
import dcraft.hub.app.IEventSubscriber;
import dcraft.hub.config.CoreLoaderWork;
import dcraft.hub.resource.ResourceTier;
import dcraft.log.Logger;
import dcraft.task.TaskContext;
import dcraft.task.scheduler.limit.LimitHelper;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class StandardSchedulerStart extends CoreLoaderWork {
	@Override
	public void firstload(TaskContext taskctx, ResourceTier tier) {
		Logger.debug("Initializing Task Scheduler");
		
		XElement config = tier.getConfig().getTag("Scheduler");
		
		if (config == null)
			config = XElement.tag("Scheduler");
		
		for (XElement el : config.selectAll("Batch")) {
			XElement bel = el.find("Limits");
			String name = el.getAttribute("Name");
			
			if (StringUtil.isNotEmpty(name) && (bel != null)) {
				LimitHelper h = new LimitHelper();
				h.init(bel);
				ScheduleHub.batches.put(name, h);
			}
		}
		
		// these features require an application restart
		if (! ApplicationHub.isOperational()) {
			// setup the provider of the work queue
			/* TODO
			String classname = config.getAttribute("InterfaceClass");
			
			if (StringUtil.isEmpty(classname))
				classname = "dcraft.task.scheduler.LocalSchedulerDriver";
			
			if (StringUtil.isNotEmpty(classname)) {
				Object impl =  ApplicationHub.getInstance(classname);
				
				if ((impl == null) || !(impl instanceof ISchedulerDriver)) {
					Logger.errorTr(227, classname);
					return false;
				}
				
				ScheduleHub.driver = (ISchedulerDriver)impl;
				ScheduleHub.driver.init(config);
			}
			*/
				
				// remember that sys workers should not use OperationContext
			/*
			ApplicationHub.getClock().addSlowSystemWorker(new ISystemWork() {
				@Override
				public void run(SysReporter reporter) {
					reporter.setStatus("before schedule update");
					
					if (ScheduleHub.active) {
						// TODO check for updates to the schedule
					}
					
					reporter.setStatus("after schedule update");
				}
				
				@Override
				public int period() {
					return 60;
				}
			});
			*/
			
			ApplicationHub.subscribeToEvents(new IEventSubscriber() {
				@Override
				public void eventFired(Integer event, Object e) {
					if (event == HubEvents.HubState) {
						if (e == HubState.Running) {
							ScheduleHub.setActive(true);
						} else {
							ScheduleHub.setActive(false);
						}
					}
				}
			});
		}
		
		ScheduleHub.setActive(true);
		
		//Logger.error("Unable to initialize scheduler Hub");
		
		taskctx.returnEmpty();
	}
	
	@Override
	public void reload(TaskContext taskctx, ResourceTier tier) {
		this.firstload(taskctx, tier);
	}
}
