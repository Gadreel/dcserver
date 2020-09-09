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
import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.inst.Instruction;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.ReturnOption;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class ConfigGetTagLocal extends Instruction {
	static public ConfigGetTagLocal tag() {
		ConfigGetTagLocal el = new ConfigGetTagLocal();
		el.setName("dcs.ConfigGetTagLocal");
		return el;
	}

	@Override
	public XElement newNode() {
		return ConfigGetTagLocal.tag();
	}

	@Override
	public ReturnOption run(InstructionWork stack) throws OperatingContextException {
		if (stack.getState() == ExecuteState.READY) {
			String tag = StackUtil.stringFromSource(stack, "Tag");

			XElement settings = ResourceHub.getResources().getConfig().getTagLocal(tag);

			if (settings != null) {
				String result = StackUtil.stringFromSource(stack, "Result");
				
				if (StringUtil.isNotEmpty(result)) {
					StackUtil.addVariable(stack, result, settings);
				}
			}
			
			return ReturnOption.CONTINUE;
		}

		return ReturnOption.CONTINUE;
	}
}
