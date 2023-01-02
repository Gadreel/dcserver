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
import dcraft.script.inst.Instruction;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.ReturnOption;
import dcraft.struct.BaseStruct;
import dcraft.struct.CompositeParser;
import dcraft.struct.CompositeStruct;
import dcraft.struct.scalar.NullStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;
import dcraft.xml.XmlReader;

public class ParseJson extends Instruction {
	static public ParseJson tag() {
		ParseJson el = new ParseJson();
		el.setName("dcs.ParseJson");
		return el;
	}

	@Override
	public XElement newNode() {
		return ParseJson.tag();
	}

	@Override
	public ReturnOption run(InstructionWork stack) throws OperatingContextException {
		if (stack.getState() == ExecuteState.READY) {
			BaseStruct source = StackUtil.refFromSource(stack, "Source");
			String result = StackUtil.stringFromSource(stack, "Result");
			BaseStruct struct = NullStruct.instance;

			if (source instanceof StringStruct)
				struct = CompositeParser.parseJson(((StringStruct)source).getValue());

			if (StringUtil.isNotEmpty(result))
				StackUtil.addVariable(stack, result, struct);

			return ReturnOption.CONTINUE;
		}

		return ReturnOption.CONTINUE;
	}
}
