/* ************************************************************************
#
#  designCraft.io
#
#  http://designcraft.io/
#
#  Copyright:
#    Copyright 2014 eTimeline, LLC. All rights reserved.
#
#  License:
#    See the license.txt file in the project's top-level directory for details.
#
#  Authors:
#    * Andy White
#
************************************************************************ */
package dcraft.task.scheduler.driver;

import dcraft.hub.ResourceHub;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.task.Task;
import dcraft.task.TaskHub;
import dcraft.util.StringUtil;

// TODO rework, this doesn't quite cut it, nor does schedule driver 
public class ScheduleEntry {
	static public enum ScheduleArea {
		Team,
		Local
	}
	
	protected RecordStruct params = null;
	protected Task task = null;
	protected String provider = null;
	protected ScheduleArea area = null;
	protected String scheduleId = null;
	protected String title = null;
	
	public String getScheduleId() {
		return this.scheduleId;
	}
	
	public void setScheduleId(String scheduleId) {
		this.scheduleId = scheduleId;
	}
	
	public String getTitle() {
		return this.title;
	}
	
	public void setTitle(String title) {
		this.title = title;
	}
	
	public Task getTask() {
		return this.task;
	}
	
	public void setTask(Task task) {
		this.task = task;
	}
	
	public String getProvider() {
		return this.provider;
	}
	
	public void setProvider(String provider) {
		this.provider = provider;
	}
	
	public ScheduleArea getArea() {
		return this.area;
	}
	
	public void setArea(ScheduleArea area) {
		this.area = area;
	}
	
	public RecordStruct getParams() {
		return this.params;
	}
	
	public void setParams(RecordStruct params) {
		this.params = params;
	}

	public boolean prepareTask() {
		if (StringUtil.isNotEmpty(this.provider)) {
			if (this.provider.startsWith("@")) {
				ITaskProvider prov = (ITaskProvider) ResourceHub.getResources().getClassLoader().getInstance(this.provider.substring(1));
				
				if (prov != null) {
					this.task = prov.getTask(this);
					return true;
				}

				Logger.error("Could not load task provider for schedule");
				return false;
			}		
			
			if (this.provider.startsWith("$")) {
				Task t = Task.ofHubRoot()		// TODO need a way to collect the context from this - Site/Tenant
					.withTitle(this.title)
					.withParams(this.params);
				
				/* RODO restore scripts
				if (ScriptWork.addScript(t, Paths.get(this.provider.substring(1)))) {
					this.task = t;
					return true;
				}
				*/
				
				Logger.error("Error compiling scriptold");
				return false;
			}		
		}
		
		Logger.error("No task defined for schedule");
		return false;
	}
	
	// TODO rework this all
	public boolean submit() {
		// first prepare the task, which relies on
		if (! this.prepareTask())	// TODO not ideal
			return false;
		
		this.task.prep();	// TODO not ideal
		
		RecordStruct hints = this.task.getCreateHints();
		
		hints.with("_ScheduleId", this.scheduleId);
		// TODO params.withField("_ScheduleHints", this.hints);
		
		// TODO figure out how to get correct task run info to the scheduler so it can reschedule correctly
		// add a task observer that, when run is done, provides the scheduler with the run for analysis and continuation scheduling
		
		if (this.area == ScheduleArea.Local) {
			TaskHub.submit(this.task);
			return true;
		}
		
		/* TODO
		if (this.area == ScheduleArea.Team) {
			TaskQueueHub.reserveUniqueAndSubmit(this.task);
			
			// don't error further even if we could not reserve
			return true;
		}
		*/
		
		Logger.error("Unable to submit scheduled task");
		return false;
	}
}
