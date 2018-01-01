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
import dcraft.scriptold.BlockInstruction;
import dcraft.scriptold.ExecuteState;
import dcraft.scriptold.StackEntry;
import dcraft.struct.Struct;

public class IfNotEmpty extends BlockInstruction {
    @Override
    public void run(StackEntry stack) throws OperatingContextException {
        // if we do not pass logical condition then mark as done so we will skip this block
        // note that for the sake of nice debugging we do not set Done state here, would cause skip in debugger
		if (stack.getState() == ExecuteState.Ready) {
	        Struct target = source.hasAttribute("Target")
	        		? stack.refFromElement(source, "Target")
	        	    : stack.queryVariable("_LastResult");
	        		
            boolean pass = (target != null) ? !target.isEmpty() : false;	        		
			stack.getStore().with("Pass", pass);
			
			if (!pass)
				stack.setState(ExecuteState.Done); 
		}
		
    	super.run(stack);
    	
		if (stack.getState() == ExecuteState.Done) 
    		stack.getParent().addVariable("_LastIf", stack.getStore().getField("Pass"));
    }

}
