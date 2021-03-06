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

import java.time.ZonedDateTime;

import dcraft.task.scheduler.limit.LimitHelper;
import dcraft.xml.XElement;

public interface IScheduleHelper {
	void init(CommonSchedule schedule, XElement config);
	void setLimits(LimitHelper limits);   // limits for the helper to check
	void setLast(ZonedDateTime last);		// set only once
	ZonedDateTime next();					// call-able multiple times, each time gets the next
}
