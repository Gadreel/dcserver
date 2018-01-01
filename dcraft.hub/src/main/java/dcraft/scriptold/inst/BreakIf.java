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
package dcraft.scriptold.inst;

import dcraft.hub.op.OperatingContextException;
import dcraft.scriptold.ExecuteState;
import dcraft.scriptold.Instruction;
import dcraft.scriptold.LogicBlockInstruction;
import dcraft.scriptold.StackEntry;
import dcraft.struct.ScalarStruct;
import dcraft.struct.Struct;

public class BreakIf extends Instruction {
	@Override
	public void run(StackEntry stack) throws OperatingContextException {
        Struct target = this.source.hasAttribute("Target")
        		? stack.refFromElement(this.source, "Target")
        	    : stack.queryVariable("_LastResult");

        if (LogicBlockInstruction.checkLogic(stack, (ScalarStruct)target, this.source))		
        	stack.setState(ExecuteState.Break);
        else
        	stack.setState(ExecuteState.Done);
        
		stack.resume();
	}
	
	@Override
	public void cancel(StackEntry stack) {
		// do nothing, this isn't cancellable
	}
}
