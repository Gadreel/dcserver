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

public class Result extends Instruction {
	static public Result tag() {
		Result el = new Result();
		el.setName("dcs.Result");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Result.tag();
	}
	
	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		IResultAwareWork resultAwareWork = StackUtil.queryResultAware(state);
		
        if (this.hasAttribute("Value")) {
			BaseStruct target = StackUtil.refFromElement(state, this, "Value", true);
        	resultAwareWork.setResult(target);
        }
        
        if (StackUtil.boolFromSource(state, "ResetFlag", true))
        	resultAwareWork.clearExitCode();
        
		return ReturnOption.CONTINUE;
	}
}
