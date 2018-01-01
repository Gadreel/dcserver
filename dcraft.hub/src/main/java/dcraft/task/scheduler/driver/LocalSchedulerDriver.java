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

import java.util.HashMap;

import dcraft.log.Logger;
import dcraft.struct.CompositeParser;
import dcraft.struct.CompositeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.Task;
import dcraft.task.scheduler.driver.ScheduleEntry.ScheduleArea;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class LocalSchedulerDriver implements ISchedulerDriver {
	protected ListStruct schedule = new ListStruct();
	protected HashMap<String, ScheduleEntry> entries = new HashMap<>();
	
	@Override
	public void init(XElement config) {
		if (config != null) {
			for (XElement task : config.selectAll("Task")) {
				String id = task.getAttribute("Id");
				
				if (StringUtil.isEmpty(id))
					id = Task.nextTaskId();
				
				String title = task.getAttribute("Title");
				
				XElement sched = task.find("CommonSchedule");
				
				if (sched == null)
					sched = task.find("SimpleSchedule");
				
				this.schedule.withItem(
					RecordStruct.record()
						.with("Id", id)
						.with("Title", title)
						.with("Schedule", sched)
				);	
				
				ScheduleEntry entry = new ScheduleEntry();
				
				entry.setScheduleId(id);
				entry.setArea(ScheduleArea.Local);
				entry.setTitle(title);
				entry.setProvider("$" + task.getAttribute("Script"));
				
				String params = task.selectFirstText("Params");
				
				if (StringUtil.isNotEmpty(params)) {
					CompositeStruct pres = CompositeParser.parseJson(params);
					
					if (pres != null)
						entry.setParams((RecordStruct) pres);
				}
				
				this.entries.put(id, entry);
			}
		}
	}

	@Override
	public void start() {
		Logger.infoTr(225);
	}

	@Override
	public void stop() {
		Logger.infoTr(226);
	}

	@Override
	public ListStruct loadSchedule() {
		return this.schedule;
	}
	
	@Override
	public ScheduleEntry loadEntry(String id) {
		ScheduleEntry entry = this.entries.get(id);
		
		if (entry != null) 
			return entry;
		
		Logger.errorTr(166, id);
		return null;
	}
}
