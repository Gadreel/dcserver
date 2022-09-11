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
import dcraft.script.work.ReturnOption;
import dcraft.struct.BaseStruct;
import dcraft.struct.Struct;
import dcraft.task.IResultAwareWork;
import dcraft.xml.XElement;

/**
 * Return does not work inside a loop, so define a result variable outside the loop and set inside loop, then use a Break to get out
 *
 * TODO update so it exits the function even from a loop - new ReturnOption
 */
public class Return extends Instruction {
	static public Return tag() {
		Return el = new Return();
		el.setName("dcs.Return");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Return.tag();
	}
	
	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		IResultAwareWork resultAwareWork = StackUtil.queryResultAware(state);
		
        if (this.hasAttribute("Result")) {
			BaseStruct target = StackUtil.refFromElement(state, this, "Result", true);
        	resultAwareWork.setResult(target);
        }
        
        if (StackUtil.boolFromSource(state, "ResetFlag", true))
        	resultAwareWork.clearExitCode();
        
		return ReturnOption.CONTROL_BREAK;
	}
}
