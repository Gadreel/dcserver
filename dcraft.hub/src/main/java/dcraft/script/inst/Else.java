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
import dcraft.script.StackUtil;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.ReturnOption;
import dcraft.struct.BaseStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.BooleanStruct;
import dcraft.xml.XElement;

public class Else extends BlockInstruction {
	static public Else tag() {
		Else el = new Else();
		el.setName("dcs.Else");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Else.tag();
	}
	
	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
        // if we do not pass logical condition then mark as done so we will skip this block
        // note that for the sake of nice debugging we do not set Done state here, would cause skip in debugger
		if (state.getState() == ExecuteState.READY) {

			BaseStruct passvar = StackUtil.queryVariable(state.getParent(), "_LastIf");
			boolean ifpass = false;
			
			if ((passvar != null) && (passvar instanceof BooleanStruct))
				ifpass = ((BooleanStruct)passvar).getValue();
			
			if (ifpass)
				state.setState(ExecuteState.DONE);
			else if (this.gotoTop(state))
				return ReturnOption.CONTINUE;
		}
		else {
			// each resume should go to next child instruction until we run out
			if (this.gotoNext(state, false))
				return ReturnOption.CONTINUE;
		}

		return ReturnOption.DONE;
    }
}
