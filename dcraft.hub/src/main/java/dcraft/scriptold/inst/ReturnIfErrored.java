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
import dcraft.scriptold.StackEntry;
import dcraft.struct.Struct;

public class ReturnIfErrored extends Instruction {
	@Override
	public void run(StackEntry stack) throws OperatingContextException {
		if (!stack.getActivity().hasErrored()) {
			stack.setState(ExecuteState.Done);
			stack.resume();
			return;
		}
		
        if (this.source.hasAttribute("Target")) {
        	Struct target = stack.refFromElement(this.source, "Target");        	
        	stack.getExecutingStack().setLastResult(target);
        }
        
        if (stack.boolFromSource("ResetFlag", true))
        	stack.getActivity().clearErrored();
        
    	stack.setState(ExecuteState.Break);
		stack.resume();
	}
	
	@Override
	public void cancel(StackEntry stack) {
		// do nothing, this isn't cancellable
	}
}
