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
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.work.BlockWork;
import dcraft.script.work.CallFuncWork;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.ReturnOption;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.AnyStruct;
import dcraft.struct.scalar.IntegerStruct;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

public class CallFunction extends Instruction {
	static public CallFunction tag() {
		CallFunction el = new CallFunction();
		el.setName("dcs.CallFunc");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return CallFunction.tag();
	}
	
	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		Struct funcwrap = StackUtil.queryVariable(state, StackUtil.stringFromSource(state, "Name"));
		
		if (funcwrap == null) {
			Logger.error("Did not find function");
			return ReturnOption.DONE;
		}
		
		InstructionWork func = ((Instruction) ((AnyStruct) funcwrap).getValue()).createStack(state);
		
		func.getStore().with("CallMode", "True");
		
		// TODO add _Arg
		
		((CallFuncWork) state).setCurrEntry(func);
		
		return func.run();
	}
	
	@Override
	public InstructionWork createStack(IParentAwareWork state) {
		return CallFuncWork.of(state, this);
	}
}
