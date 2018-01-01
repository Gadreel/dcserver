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

import dcraft.struct.ListStruct;
import dcraft.xml.XElement;

public interface ISchedulerDriver {
	void init(XElement config);	
	void start();	
	void stop();	
	
	ListStruct loadSchedule();
	ScheduleEntry loadEntry(String id);
}
