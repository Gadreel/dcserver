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
package dcraft.script.inst.ext;

import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.inst.Instruction;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.ReturnOption;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class ConfigGetTag extends Instruction {
	static public ConfigGetTag tag() {
		ConfigGetTag el = new ConfigGetTag();
		el.setName("dcs.ConfigGetTag");
		return el;
	}

	@Override
	public XElement newNode() {
		return ConfigGetTag.tag();
	}

	@Override
	public ReturnOption run(InstructionWork stack) throws OperatingContextException {
		if (stack.getState() == ExecuteState.READY) {
			String tag = StackUtil.stringFromSource(stack, "Tag");

			XElement settings = ResourceHub.getResources().getConfig().getTag(tag);

			if (settings != null) {
				String result = StackUtil.stringFromSource(stack, "Result");
				
				if (StringUtil.isNotEmpty(result)) {
					Struct var = settings;
					
					StackUtil.addVariable(stack, result, var);
				}
			}
			
			return ReturnOption.CONTINUE;
		}

		return ReturnOption.CONTINUE;
	}
}
