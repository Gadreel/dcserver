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
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.ReturnOption;
import dcraft.xml.XElement;

// nothing special, just holds a Name
public class Switch extends BlockInstruction {
	static public Switch tag() {
		Switch el = new Switch();
		el.setName("dcs.Switch");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Switch.tag();
	}
	
	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		// TODO run only the first matching case
		if (state.getState() == ExecuteState.READY) {
			if (this.gotoTop(state))
				return ReturnOption.CONTINUE;
		}
		else {
			if (this.gotoNext(state, false))
				return ReturnOption.CONTINUE;
		}
		
		return ReturnOption.DONE;
	}
}
