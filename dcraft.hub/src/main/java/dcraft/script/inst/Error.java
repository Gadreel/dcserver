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

import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.ReturnOption;
import dcraft.task.IResultAwareWork;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.util.List;

public class Error extends Instruction {
	static public Error tag() {
		Error el = new Error();
		el.setName("dcs.Error");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Error.tag();
	}
	
	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		String output = this.hasText() ? StackUtil.resolveValueToString(state, this.getText()) : null;
		long code = StackUtil.intFromSource(state,"Code", 0);
		
		if (StringUtil.isEmpty(output)) {
			List<XElement> params = this.selectAll("Param");
			Object[] oparams = new Object[params.size()];
			
			for (int i = 0; i < params.size(); i++) 
				oparams[i] = StackUtil.refFromElement(state, params.get(i), "Value", true).toString();
			
			output = ResourceHub.tr("_code_" + code, oparams);
		}
		
		Logger.error(code, output);
		
		IResultAwareWork resultAwareWork = StackUtil.queryResultAware(state);
		
		if (resultAwareWork != null)
			resultAwareWork.setExitCode(code, output);
		
		return ReturnOption.CONTINUE;
	}
}
