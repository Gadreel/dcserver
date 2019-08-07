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
import dcraft.schema.SchemaHub;
import dcraft.script.StackUtil;
import dcraft.script.work.BlockWork;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.ReturnOption;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.AnyStruct;
import dcraft.struct.scalar.IntegerStruct;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

public class ForEach extends BlockInstruction {
	static public ForEach tag() {
		ForEach el = new ForEach();
		el.setName("dcs.ForEach");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return ForEach.tag();
	}
	
	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		//RecordStruct store = state.getStore();

		// "_forindex"
		String name = StackUtil.stringFromSource(state,"Name","_forvalue");

		Struct source = StackUtil.refFromSource(state,"In", true);
		
		if (source instanceof XElement) {
			source = ListStruct.list(((XElement)source).selectAll("*"));
		}

		// TODO support more than just ListStruct and XElement someday
		if (! (source instanceof ListStruct)) {
			Logger.error("Expected ListStruct in ForEach");
			return ReturnOption.DONE;
		}

		ListStruct listSource = (ListStruct) source;
		IntegerStruct pos = null;

		if (state.getState() == ExecuteState.READY) {
			pos = IntegerStruct.of(0L);

			StackUtil.addVariable(state, "_forindex", pos);
		}
		else {
			// each resume should go to next child instruction until we run out, then check logic
			if (! ((BlockWork) state).checkClearContinueFlag() && this.gotoNext(state, false))
				return ReturnOption.CONTINUE;

			pos = (IntegerStruct) StackUtil.queryVariable(state, "_forindex");

			pos.setValue(pos.getValue() + 1);
		}

		if (pos.getValue() >= listSource.size())
			return ReturnOption.DONE;

		Struct val = listSource.getItem(pos.getValue().intValue());

		StackUtil.addVariable(state, name, val);

		if (this.gotoTop(state))
			return ReturnOption.CONTINUE;

		return ReturnOption.DONE;
	}
	
	@Override
	public InstructionWork createStack(IParentAwareWork state) {
		return BlockWork.of(state, this)
				.withControlAware(true);
	}
}
