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
import dcraft.log.Logger;
import dcraft.script.*;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.OperationsWork;
import dcraft.script.work.ReturnOption;
import dcraft.struct.ScalarStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.StringStruct;
import dcraft.xml.XElement;

public class With extends OperationsInstruction {
	static public With tag() {
		With el = new With();
		el.setName("dcs.With");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return With.tag();
	}
	
	// like BlockInstruction return DONE means done with this block/ops - not done with Script as with other instructions
	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		if (state.getState() == ExecuteState.READY) {
			Struct var = StackUtil.refFromSource(state,"Target");
			
			// don't check empty, needs to be able to handle empty
			if (this.hasAttribute("SetTo")) {
				Struct var3 = StackUtil.refFromSource(state, "SetTo");
				
				if (var instanceof ScalarStruct)
					((ScalarStruct) var).adaptValue(var3);
				else
					var = var3;
			}

			if (var == null) {
				Logger.errorTr(520);
				return ReturnOption.DONE;
			}
			
			((OperationsWork) state).setTarget(var);
			
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
