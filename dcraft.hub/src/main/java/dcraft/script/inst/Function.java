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
import dcraft.script.work.BlockWork;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.ReturnOption;
import dcraft.struct.BaseStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.AnyStruct;
import dcraft.struct.scalar.NullStruct;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class Function extends BlockInstruction {
	static public Function tag() {
		Function el = new Function();
		el.setName("dcs.Function");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Function.tag();
	}
	
	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		BlockWork fstack = (BlockWork)state;
		
		if (state.getState() == ExecuteState.READY) {
			// if not in call mode then the function is just registering for later use
			if (! state.getStore().getFieldAsBooleanOrFalse("CallMode")) {
				StackUtil.addVariable(state.getParent(), StackUtil.stringFromSource(state, "Name"), AnyStruct.of(this));
				return ReturnOption.DONE;
			}

			BaseStruct param = StackUtil.queryVariable(state, "_Arg");
			
			if (param == null)
				param = NullStruct.instance;
			
			fstack.addVariable("_Param", param);
			
			String pname = StackUtil.stringFromSource(state, "Param");
			
			if (StringUtil.isNotEmpty(pname))
				fstack.addVariable(pname, param);
			
			if (this.gotoTop(state))
				return ReturnOption.CONTINUE;
		}
		else {
			// each resume should go to next child instruction until we run out
			if (this.gotoNext(state, false))
				return ReturnOption.CONTINUE;
		}
		
		return ReturnOption.DONE;
    }
}
