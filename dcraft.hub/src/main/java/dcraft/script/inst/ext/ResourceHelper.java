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
import dcraft.hub.resource.ResourceTier;
import dcraft.schema.DataType;
import dcraft.script.StackUtil;
import dcraft.script.inst.Instruction;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.ReturnOption;
import dcraft.struct.CompositeStruct;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

// TODO replace with ResourceHelper
public class ResourceHelper extends Instruction {
	static public ResourceHelper tag() {
		ResourceHelper el = new ResourceHelper();
		el.setName("dcs.ResourceHelper");
		return el;
	}

	@Override
	public XElement newNode() {
		return ResourceHelper.tag();
	}

	@Override
	public ReturnOption run(InstructionWork stack) throws OperatingContextException {
		if (stack.getState() == ExecuteState.READY) {
			String level = StackUtil.stringFromSource(stack, "Level", "My");
			String find = StackUtil.stringFromSource(stack, "Find");

			ResourceTier tier = null;

			switch (level) {
				case "My":
					tier = ResourceHub.getResources();
					break;
				case "Site":
					tier = ResourceHub.getSiteResources();
					break;
				case "Tenant":
					tier = ResourceHub.getTenantResources();
					break;
				default:
					tier = ResourceHub.getTopResources();
					break;
			}

			CompositeStruct struct = tier.get(find);

			if (struct != null) {
				String result = StackUtil.stringFromSource(stack, "Result");
				
				if (StringUtil.isNotEmpty(result)) {
					StackUtil.addVariable(stack, result, struct);
				}
			}
			
			return ReturnOption.CONTINUE;
		}

		return ReturnOption.CONTINUE;
	}
}
