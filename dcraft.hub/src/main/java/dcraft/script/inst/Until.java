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
import dcraft.script.work.BlockWork;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.ReturnOption;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

public class Until extends LogicBlockInstruction {
	static public Until tag() {
		Until el = new Until();
		el.setName("dcs.Until");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Until.tag();
	}
	
	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
        // if we do not pass logical condition then mark as done so we will skip this block
        // note that for the sake of nice debugging we do not set Done state here, would cause skip in debugger
		if (state.getState() == ExecuteState.READY) {
			if (this.gotoTop(state))
				return ReturnOption.CONTINUE;
		}
		else {
			// each resume should go to next child instruction until we run out
			if (! ((BlockWork) state).checkClearContinueFlag() && this.gotoNext(state, false))
				return ReturnOption.CONTINUE;

			if (! this.checkLogic(state) && this.gotoTop(state))
				return ReturnOption.CONTINUE;
		}

		return ReturnOption.DONE;
    }

	@Override
	public InstructionWork createStack(IParentAwareWork state) {
		return BlockWork.of(state, this)
				.withControlAware(true);
	}
}
