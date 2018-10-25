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

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.ReturnOption;
import dcraft.task.TaskContext;
import dcraft.xml.XElement;

import java.util.concurrent.ScheduledFuture;

public class Sleep extends Instruction {
	static public Sleep tag() {
		Sleep el = new Sleep();
		el.setName("dcs.Sleep");
		return el;
	}
	
	static public Sleep tag(int seconds) {
		Sleep el = new Sleep();
		el.setName("dcs.Sleep");
		el.withAttribute("Seconds", seconds + "");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Sleep.tag();
	}
	
	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		if (state.getState() == ExecuteState.READY) {
			state.getStore().removeField("Future");

			// TODO support Period or Duration also
			int secs = (int) StackUtil.intFromSource(state, "Seconds", 1);
			TaskContext ctx = OperationContext.getAsTaskOrThrow();

			// TODO review
			// if we are inside a task we have only 1 minute, what if sleep is longer?
			// automatically change timeout for the instruction
			int omin2 = ctx.getTask().getTimeout();
			int pmin = (secs / 60) + 1;  // convert sleep to minutes
			ctx.getTask().withTimeout(pmin);        // up the timeout for this instruction

			int omin = omin2;

			// TODO review if shutdown leaves Script thread hanging and uncanceled if sleep is long
			state.getStore().with("Future", ApplicationHub.getClock().scheduleOnceInternal(() -> {
				//System.out.println("after sleep point");
				state.getStore().removeField("Future");

				// ensure we are working with the correct context during resume
				OperationContext.set(ctx);

				ctx.getTask().withTimeout(omin);        // restore the original timeout

				state.setState(ExecuteState.RESUME);

				try {
					ctx.resume();
				}
				catch (Exception x) {
					Logger.error("Unable to resume after SLEEP inst: " + x);
				}
			}, secs));

			return ReturnOption.AWAIT;
		}

		return ReturnOption.CONTINUE;
	}

	@Override
	public void cancel(InstructionWork state) {
		// avoid race condition
		ScheduledFuture<?> lf = (ScheduledFuture<?>) state.getStore().getFieldAsAny("Future");

		if (lf != null) {
			lf.cancel(false);

			state.getStore().removeField("Future");
		}

		super.cancel(state);
	}
}
