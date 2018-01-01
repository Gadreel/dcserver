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
import dcraft.script.*;
import dcraft.script.work.BlockWork;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.ReturnOption;
import dcraft.struct.RecordStruct;
import dcraft.struct.scalar.IntegerStruct;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

public class For extends BlockInstruction {
	static public For tag() {
		For el = new For();
		el.setName("dcs.For");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return For.tag();
	}
	
	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		RecordStruct store = state.getStore();
		
		if (state.getState() == ExecuteState.READY) {
			long from = StackUtil.intFromSource(state,"From", 0);
			long to = StackUtil.intFromSource(state,"To", 0);
			long step = StackUtil.intFromSource(state,"Step", 1);

			IntegerStruct cntvar = IntegerStruct.of(from);

    		store.with("Counter", cntvar);
    	    store.with("To", to);
    	    store.with("Step", step);

			StackUtil.addVariable(state, StackUtil.stringFromSource(state,"Name", "_forindex"), cntvar);
			
			boolean isdone = (step > 0) ? (cntvar.getValue() > to) : (cntvar.getValue() < to);
			
			if (! isdone && this.gotoTop(state))
				return ReturnOption.CONTINUE;
		}
		else {
			// each resume should go to next child instruction until we run out, then check logic
			if (! ((BlockWork) state).checkClearContinueFlag() && this.gotoNext(state, false))
				return ReturnOption.CONTINUE;
			
			// if no NEXT child then STEP
			long to = store.getFieldAsInteger("To");
			long step = store.getFieldAsInteger("Step");
			
			IntegerStruct cntvar = (IntegerStruct) store.getField("Counter");
			cntvar.setValue(cntvar.getValue() + step);
			
			boolean isdone = (step > 0) ? (cntvar.getValue() > to) : (cntvar.getValue() < to);
			
			if (! isdone && this.gotoTop(state))
				return ReturnOption.CONTINUE;
		}
		
		return ReturnOption.DONE;
	}
	
	@Override
	public InstructionWork createStack(IParentAwareWork state) {
		return BlockWork.of(state, this)
				.withControlAware(true);
	}
}
