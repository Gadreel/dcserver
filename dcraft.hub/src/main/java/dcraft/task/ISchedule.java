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
package dcraft.task;

import dcraft.struct.RecordStruct;
import dcraft.xml.XElement;

public interface ISchedule {
	void init(XElement config);
	Task getTask();
	String getCreateId();
	String getTitle();
	void setId(String v);
	boolean reschedule();
	long when();
	void cancel();
	boolean isCancelled();
	RecordStruct getHints();
	void setHint(String name, Object value);
}
