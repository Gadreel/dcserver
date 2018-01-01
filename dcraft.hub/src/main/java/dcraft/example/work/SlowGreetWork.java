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
import dcraft.struct.Struct;
import dcraft.task.IWork;
import dcraft.task.TaskContext;
import dcraft.task.run.WorkHub;
import dcraft.task.run.WorkTopic;
import dcraft.util.FileUtil;
import dcraft.util.RndUtil;
import dcraft.util.StringUtil;

/*
 * This work pauses for 2 seconds, then print a greeting. Sometimes
 * it randomly errors out even if called correctly.
 */
public class SlowGreetWork implements IWork {
	@Override
	public void run(TaskContext ctx) throws OperatingContextException {
		// collect and print the name of the topic work is running in
		WorkTopic buck = WorkHub.getTopicOrDefault(ctx);
		
		Logger.info("Slow Greet Task attempting to run in Topic: " + buck.getName() + " current load: " + buck.inprogress());
		
		// wait a little while
		try {
			Thread.sleep(2000);
		} 
		catch (InterruptedException x) {
			System.out.println("slow greet interrupted");
		}
		
		// get the parameters from caller
		RecordStruct params = Struct.objectToRecord(ctx.getParams());
		
		if (params == null) {
			Logger.error("Unable to Greet, missing params structure.");
			ctx.setExitCode(100, "Missing params");
			ctx.returnEmpty();
			return;
		}
		
		String name = params.getFieldAsString("Greet");
		
		if (StringUtil.isEmpty(name)) {
			Logger.error("Unable to Greet, missing Greet param.");
			ctx.setExitCode(101, "Missing Greet param");
			ctx.returnEmpty();
			return;
		}
		
		// randomly error out even when called correctly
		if (RndUtil.testrnd.nextInt(10) < 3) {
			Logger.error("Unable to Greet, internal error.");
			ctx.setExitCode(102, "Internal error");
			ctx.returnEmpty();
			return;
		}
		
		// print the greeting
		Logger.info("Greetings " + name);		
		ctx.returnEmpty();
	}
}
