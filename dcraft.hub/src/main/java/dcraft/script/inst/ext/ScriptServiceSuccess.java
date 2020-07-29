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

import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.inst.*;
import dcraft.script.inst.file.Stream;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.ReturnOption;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.xml.XElement;
import dcraft.xml.XNode;

import java.util.List;

public class ScriptServiceSuccess extends Instruction {
	static public ScriptServiceSuccess tag() {
		ScriptServiceSuccess el = new ScriptServiceSuccess();
		el.setName("dcs.ScriptServiceSuccess");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return ScriptServiceSuccess.tag();
	}
	
	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		RecordStruct response = Struct.objectToRecord(StackUtil.queryVariable(state, "Response"));

		response.with("Code", "0");

		if (this.hasAttribute("Result")) {
			Struct value = StackUtil.resolveReference(state, this.attr("Result"), true);

			response.with("Result", value);
		}

		return ReturnOption.CONTINUE;
	}
}
