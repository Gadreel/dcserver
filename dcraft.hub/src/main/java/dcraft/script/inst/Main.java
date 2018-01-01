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
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.MainWork;
import dcraft.script.work.ReturnOption;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

public class Main extends BlockInstruction {
	static public Main tag() {
		Main el = new Main();
		el.setName("dcs.Main");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Main.tag();
	}
	
	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		if (state.getState() == ExecuteState.READY) {
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
	
	@Override
	public InstructionWork createStack(IParentAwareWork state) {
		return MainWork.of(state, this);
	}
	
	public InstructionWork createStack() {
		return MainWork.of(null, this);
	}
}
