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
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.OperationsWork;
import dcraft.script.work.ReturnOption;
import dcraft.struct.ScalarStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class Global extends OperationsInstruction {
	static public Global tag() {
		Global el = new Global();
		el.setName("dcs.Global");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Global.tag();
	}
	
	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		if (state.getState() == ExecuteState.READY) {
			String def = StackUtil.stringFromSource(state, "Type");
			String name = StackUtil.stringFromSource(state, "Name");
			
			Struct var = null;
			
			if (StringUtil.isNotEmpty(def))
				var = ResourceHub.getResources().getSchema().getType(def).create();
			
			if (this.hasNotEmptyAttribute("SetTo")) {
				Struct var3 = StackUtil.refFromSource(state, "SetTo");
				
				if (var3 == null) {
					Logger.errorTr(522);
					return ReturnOption.DONE;
				}
				
				if (var == null)
					var = var3.getType().create();
				
				if (var instanceof ScalarStruct)
					((ScalarStruct) var).adaptValue(var3);
				else
					var = var3;
			}
			
			if (var == null) {
				Logger.errorTr(520);
				return ReturnOption.DONE;
			}
			
			OperationContext.getOrThrow().addVariable(name, var);
			
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
