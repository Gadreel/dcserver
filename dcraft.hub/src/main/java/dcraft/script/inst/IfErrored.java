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
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.ReturnOption;
import dcraft.xml.XElement;

public class IfErrored extends LogicBlockInstruction {
	static public IfErrored tag() {
		IfErrored el = new IfErrored();
		el.setName("dcs.IfErrored");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return IfErrored.tag();
	}
	
	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
        // if we do not pass logical condition then mark as done so we will skip this block
        // note that for the sake of nice debugging we do not set Done state here, would cause skip in debugger
		if (state.getState() == ExecuteState.READY) {
			boolean pass = OperationContext.getAsTaskOrThrow().hasExitErrors();

			if (StackUtil.boolFromSource(state, "Not"))
				pass = ! pass;

			state.getStore().with("Pass", pass);
			
			if (! pass)
				state.setState(ExecuteState.DONE);
			else if (this.gotoTop(state))
				return ReturnOption.CONTINUE;
		}
		else {
			// each resume should go to next child instruction until we run out
			if (this.gotoNext(state, false))
				return ReturnOption.CONTINUE;
		}

		// put in parent so can be accessed by Else
   		StackUtil.addVariable(state.getParent(), "_LastIf", state.getStore().getField("Pass"));
		
		return ReturnOption.DONE;
    }
}
