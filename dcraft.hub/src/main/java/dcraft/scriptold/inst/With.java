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
import dcraft.log.Logger;
import dcraft.scriptold.Ops;
import dcraft.scriptold.StackEntry;
import dcraft.struct.ScalarStruct;
import dcraft.struct.Struct;
import dcraft.xml.XElement;

public class With extends Ops {
	@Override
	public void prepTarget(StackEntry stack) throws OperatingContextException {
        Struct var = stack.refFromSource("Target");

        this.setTarget(stack, var);
        
		if (stack.codeHasAttribute("SetTo")) {
	        Struct var3 = stack.refFromSource("SetTo");
							
			if (var instanceof ScalarStruct) 
				((ScalarStruct) var).adaptValue(var3);
			else 
				Logger.errorTr(540);
		}
		
		this.nextOpResume(stack);
	}
	
	@Override
	public void runOp(StackEntry stack, XElement op, Struct target) throws OperatingContextException {
		stack.operate(target, op);
	}
	
	@Override
	public void cancel(StackEntry stack) {
		// do nothing, this isn't cancellable
	}
}
