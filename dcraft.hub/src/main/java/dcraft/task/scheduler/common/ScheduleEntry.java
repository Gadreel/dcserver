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
package dcraft.task.scheduler.common;

import java.time.LocalTime;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;

import dcraft.util.TimeUtil;
import dcraft.xml.XElement;

/*
 *	<Schedule At="" RunIfMissed="True/False" />
 */
public class ScheduleEntry implements Comparable<ScheduleEntry> {
	protected LocalTime time = null;
	protected boolean runIfMissed = false;
	
	public LocalTime getTime() {
		return this.time;
	}
	
	public boolean isRunIfMissed() {
		return this.runIfMissed;
	}
	
	public void init(XElement config) {
		if (config != null) { 
			if (config.getAttributeAsBooleanOrFalse("RunIfMissed"))
				this.runIfMissed = true;
		
			this.time = TimeUtil.parseLocalTime(config.getAttribute("At"));

			// scheduling at midnight does not work, but 1 ms after is fine 
			if (this.time.getLong(ChronoField.MILLI_OF_DAY) == 0) 
				this.time = this.time.plus(1, ChronoUnit.MILLIS);
		}
		
		if (this.time == null)
			this.time = LocalTime.now();
	}

	@Override
	public int compareTo(ScheduleEntry entry) {
		return this.time.compareTo(entry.getTime());
	}
}
