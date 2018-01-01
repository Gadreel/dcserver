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
import dcraft.script.work.InstructionWork;
import dcraft.script.work.ReturnOption;
import dcraft.xml.XElement;

public class Continue extends Instruction {
	static public Continue tag() {
		Continue el = new Continue();
		el.setName("dcs.Continue");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Continue.tag();
	}
	
	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		return ReturnOption.CONTROL_CONTINUE;
	}
}
