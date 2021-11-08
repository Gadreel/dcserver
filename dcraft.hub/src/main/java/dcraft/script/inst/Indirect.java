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
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.OperationsWork;
import dcraft.script.work.ReturnOption;
import dcraft.struct.BaseStruct;
import dcraft.struct.ScalarStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class Indirect extends OperationsInstruction {
	static public Indirect tag() {
		Indirect el = new Indirect();
		el.setName("dcs.Indirect");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Indirect.tag();
	}
	
	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		if (state.getState() == ExecuteState.READY) {
			String value = StackUtil.stringFromSource(state, "Value");
			String name = StackUtil.stringFromSource(state, "Name");

			BaseStruct var = null;

			if (StringUtil.isNotEmpty(value) && StringUtil.isNotEmpty(name)) {
				var = StackUtil.queryVariable(state, value);
			}

			if (var == null)
				return ReturnOption.DONE;

			StackUtil.addVariable(state, name, var);
			
			((OperationsWork) state).setTarget(var);
			
			if (this.gotoTop(state))
				return ReturnOption.CONTINUE;
		}
		else {
			if (this.gotoNext(state, false))
				return ReturnOption.CONTINUE;
		}
		
		return ReturnOption.DONE;
	}
}
