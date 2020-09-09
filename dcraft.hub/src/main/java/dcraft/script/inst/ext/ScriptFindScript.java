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

import dcraft.filestore.CommonPath;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.schema.DataType;
import dcraft.script.StackUtil;
import dcraft.script.inst.Instruction;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.ReturnOption;
import dcraft.struct.Struct;
import dcraft.struct.scalar.StringStruct;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.nio.file.Path;

public class ScriptFindScript extends Instruction {
	static public ScriptFindScript tag() {
		ScriptFindScript el = new ScriptFindScript();
		el.setName("dcs.ScriptFindScript");
		return el;
	}

	@Override
	public XElement newNode() {
		return ScriptFindScript.tag();
	}

	@Override
	public ReturnOption run(InstructionWork stack) throws OperatingContextException {
		if (stack.getState() == ExecuteState.READY) {
			String path = StackUtil.stringFromSource(stack, "Path");

			Path fnd = ResourceHub.getResources().getScripts().findScript(CommonPath.from(path));

			if (fnd != null) {
				String result = StackUtil.stringFromSource(stack, "Result");
				
				if (StringUtil.isNotEmpty(result)) {
					StackUtil.addVariable(stack, result, StringStruct.of(fnd.normalize().toString()));
				}
			}
			
			return ReturnOption.CONTINUE;
		}

		return ReturnOption.CONTINUE;
	}
}
