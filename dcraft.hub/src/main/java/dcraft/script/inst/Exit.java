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
import dcraft.struct.scalar.StringStruct;
import dcraft.task.IResultAwareWork;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.util.List;

public class Exit extends Instruction {
	static public Exit tag() {
		Exit el = new Exit();
		el.setName("dcs.Exit");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Exit.tag();
	}
	
	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		String output = this.hasText() ? StackUtil.resolveValueToString(state, this.getText()) : null;
		long code = StackUtil.intFromSource(state, "Code", 0);
		BaseStruct result = this.hasAttribute( "Result") ? StackUtil.refFromSource(state, "Result") : null;
		
		IResultAwareWork resultAwareWork = StackUtil.queryResultAware(state);
		
		if (StringUtil.isNotEmpty(output)) {
			resultAwareWork.setExitCode(code, output);
		}
		else if (this.hasAttribute("Code")) {
			List<XElement> params = this.selectAll("Param");
			Object[] oparams = new Object[params.size()];
			
			for (int i = 0; i < params.size(); i++) 
				oparams[i] = StackUtil.refFromElement(state, params.get(i), "Value").toString();
			
			resultAwareWork.setExitCodeTr(code, oparams);
		}
		
		if ((result == null) && StringUtil.isNotEmpty(output))
			result = StringStruct.of(output);
		
		if (result != null)
			resultAwareWork.setResult(result);
		
		return ReturnOption.DONE;
	}
}
