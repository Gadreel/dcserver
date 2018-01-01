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
import dcraft.hub.op.OperationContext;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.ReturnOption;
import dcraft.xml.XElement;

public class Debugger extends Instruction {
	static public Debugger tag() {
		Debugger el = new Debugger();
		el.setName("dcs.Debugger");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Debugger.tag();
	}
	
	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		OperationContext.getAsTaskOrThrow().engageDebugger();

		return ReturnOption.CONTINUE;
	}
}
