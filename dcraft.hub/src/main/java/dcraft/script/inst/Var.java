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
import dcraft.script.*;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.OperationsWork;
import dcraft.script.work.ReturnOption;
import dcraft.struct.BaseStruct;
import dcraft.struct.CompositeStruct;
import dcraft.struct.ScalarStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.StringStruct;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class Var extends OperationsInstruction {
	static public Var tag() {
		Var el = new Var();
		el.setName("dcs.Var");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Var.tag();
	}
	
	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		if (state.getState() == ExecuteState.READY) {
			String def = StackUtil.stringFromSource(state, "Type");
			String name = StackUtil.stringFromSource(state, "Name");

			BaseStruct var = null;
			
			if (StringUtil.isNotEmpty(def))
				var = ResourceHub.getResources().getSchema().getType(def).create();
			
			if (this.hasAttribute("SetTo")) {
				BaseStruct var3 = StackUtil.refFromSource(state, "SetTo", true);

				// if we cannot infer data type then error
				if ((var3 == null) && (var == null)) {
					Logger.errorTr(522);
					return ReturnOption.DONE;
				}
				
				if (var3 instanceof ScalarStruct) {
					if (var == null)
						var = var3.getType().create();

					if (var instanceof ScalarStruct) {
						((ScalarStruct) var).adaptValue(var3);
					}
					else {
						// TODO report error
					}
				}
				else {
					if (var3 instanceof CompositeStruct) {
						var = var3;
					}
					else if (var instanceof ScalarStruct) {
						((ScalarStruct) var).adaptValue(null);
					}
				}
			}

			if (var == null) {
				Logger.errorTr(520);
				return ReturnOption.DONE;
			}
			
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
