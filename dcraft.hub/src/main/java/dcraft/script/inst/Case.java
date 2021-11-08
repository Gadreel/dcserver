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
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.ReturnOption;
import dcraft.struct.BaseStruct;
import dcraft.struct.ScalarStruct;
import dcraft.struct.Struct;
import dcraft.xml.XElement;

public class Case extends LogicBlockInstruction {
	static public Case tag() {
		Case el = new Case();
		el.setName("dcs.Case");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Case.tag();
	}
	
	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		// if we do not pass logical condition then mark as done so we will skip this block
		// note that for the sake of nice debugging we do not set Done state here, would cause skip in debugger
		if (state.getState() == ExecuteState.READY) {
			// use local name in condition check if present
			// if not then try parent name
			BaseStruct var = this.hasAttribute("Target")
					? StackUtil.refFromSource(state,"Target")
					: StackUtil.refFromElement(state.getParent(), ((InstructionWork)state.getParent()).getInstruction(), "Target");
			
			if (var == null)
				Logger.trace( "Case has no variable to compare with, missing Target");
			
			if ((var == null) || ! this.checkLogic(state, var, this))
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
