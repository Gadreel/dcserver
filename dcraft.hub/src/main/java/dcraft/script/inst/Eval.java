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
import dcraft.script.work.OperationsWork;
import dcraft.script.work.ReturnOption;
import dcraft.struct.BaseStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class Eval extends Instruction {
	static public Eval tag() {
		Eval el = new Eval();
		el.setName("dcs.Eval");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Eval.tag();
	}
	
	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		BaseStruct value = StackUtil.refFromSource(state, "Value", true);
		String name = StackUtil.stringFromSource(state, "Name");

		if (value != null) {
			String evaled = StackUtil.resolveValueToString(state, value.toString(), true);

			if (StringUtil.isNotEmpty(name))
				StackUtil.addVariable(state, name, StringStruct.of(evaled));
		}

		return ReturnOption.CONTINUE;
	}
}
