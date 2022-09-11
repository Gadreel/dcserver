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
import dcraft.script.work.InstructionWork;
import dcraft.script.StackUtil;
import dcraft.script.work.ReturnOption;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.util.List;

public class Console extends Instruction {
	static public Console tag() {
		Console el = new Console();
		el.setName("dcs.Console");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Console.tag();
	}
	
	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		String output = this.hasText() ? StackUtil.resolveValueToString(state, this.getText(), true) : null;
		long code = StackUtil.intFromSource(state, "Code", 0);
		
		if (StringUtil.isEmpty(output)) {
			List<XElement> params = this.selectAll("Param");
			Object[] oparams = new Object[params.size()];
			
			for (int i = 0; i < params.size(); i++) 
				oparams[i] = StackUtil.refFromElement(state, params.get(i), "Value", true).toString();
			
			output = ResourceHub.tr("_code_" + code, oparams);
		}		
		
		if (output == null)
			System.out.println();
		else
			System.out.println(output);
		
		/* TODO restore
		IDebugger dbg = stack.getActivity().getDebugger();
		
		if (dbg != null) {
			if (output == null)
				dbg.console("");
			else
				dbg.console(output);
		}
		*/
		
		return ReturnOption.CONTINUE;
	}
}
