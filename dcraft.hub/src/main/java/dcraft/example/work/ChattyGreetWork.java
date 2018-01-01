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
import dcraft.util.FileUtil;
import dcraft.util.RndUtil;

/*
 * print greeting multiple time, then return a number
 */
public class ChattyGreetWork implements IWork {
	@Override
	public void run(TaskContext ctx) throws OperatingContextException {
		// get the parameters from caller
		RecordStruct params = ctx.getTask().getParamsAsRecord();
		
		String name = params.getFieldAsString("Greet");
		int cnt = (int) params.getFieldAsInteger("Count", 1);
		
		// print the greeting once for each count
		for (int i = 0; i < cnt; i++)
			Logger.info("Greetings " + (i + 1) + " for " + name);		
		
		ctx.returnValue(RndUtil.testrnd.nextInt(10) + 1);	// return a number between 1 and 10
	}
}
