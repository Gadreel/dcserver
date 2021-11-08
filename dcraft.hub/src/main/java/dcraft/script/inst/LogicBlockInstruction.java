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
import dcraft.script.work.InstructionWork;
import dcraft.struct.BaseStruct;
import dcraft.struct.ScalarStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.NullStruct;
import dcraft.xml.XElement;

abstract public class LogicBlockInstruction extends BlockInstruction {
    protected boolean checkLogic(InstructionWork stack) throws OperatingContextException {
        return checkLogic(stack, this);
    }

    protected boolean checkLogic(InstructionWork stack, XElement source) throws OperatingContextException {
        if (source == null) 
        	source = this;

        BaseStruct target = source.hasAttribute("Target")
        		? StackUtil.refFromElement(stack, source, "Target", true)
        	    : StackUtil.queryVariable(stack, "_LastResult");

        return LogicBlockInstruction.checkLogic(stack, target, source);
    }

    static public boolean checkLogic(InstructionWork stack, BaseStruct target, XElement source) throws OperatingContextException {
		LogicBlockState logicState = new LogicBlockState();

        if (target == null)
        	target = NullStruct.instance;

		target.checkLogic(stack, source, logicState);
	
		// if there were no conditions checked then consider the value of Target for trueness
		if (! logicState.checked)
			logicState.pass = Struct.objectToBooleanOrFalse(target);
  
		if (StackUtil.boolFromElement(stack, source, "Not"))
			logicState.pass = ! logicState.pass;

        return logicState.pass;
    }
}
