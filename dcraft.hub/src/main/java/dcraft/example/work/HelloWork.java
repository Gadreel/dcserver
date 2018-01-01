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
package dcraft.example.work;

import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.task.IWork;
import dcraft.task.TaskContext;
import dcraft.task.run.WorkHub;
import dcraft.task.run.WorkTopic;
import dcraft.util.RndUtil;
import dcraft.util.StringUtil;

/*
 * This work pauses for 2 seconds, then print a greeting. Sometimes
 * it randomly errors out even if called correctly.
 */
public class HelloWork implements IWork {
	@Override
	public void run(TaskContext ctx) throws OperatingContextException {
		// collect and print the name of the topic work is running in
		WorkTopic buck = WorkHub.getTopicOrDefault(ctx);
		
		Logger.info("Hello World! Attempting to run in Topic: " + buck.getName() + " current load: " + buck.inprogress());

		ctx.returnEmpty();
	}
}
