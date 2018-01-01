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
package dcraft.script.inst;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.ReturnOption;
import dcraft.task.TaskContext;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class Progress extends Instruction {
	static public Progress tag() {
		Progress el = new Progress();
		el.setName("dcs.Progress");
		return el;
	}

	@Override
	public XElement newNode() {
		return Progress.tag();
	}

	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		String output = this.hasText() ? StackUtil.resolveValueToString(state, this.getText()) : null;
		long code = StackUtil.intFromSource(state,"Code", 0);
		long steps = StackUtil.intFromSource(state,"Steps", -1);
		long step = StackUtil.intFromSource(state,"Step", -1);
		long amount = StackUtil.intFromSource(state,"Amount", -1);
		long add = StackUtil.intFromSource(state,"Add", -1);
		String name = StackUtil.stringFromSource(state,"Name");
		
		TaskContext ctx = OperationContext.getAsTaskOrThrow();
		
		if (amount >= 0)
			ctx.setAmountCompleted((int) amount);
		else if (add >= 0)
			ctx.setAmountCompleted(ctx.getAmountCompleted() + (int) add);
		
		if ((step >= 0) && StringUtil.isNotEmpty(name))
			ctx.setCurrentStep((int) step, name);
		
		if (steps >= 0)
			ctx.setSteps((int) steps);
		
		if (StringUtil.isNotEmpty(output))
			ctx.setProgressMessage(output);

		return ReturnOption.CONTINUE;
	}
}
