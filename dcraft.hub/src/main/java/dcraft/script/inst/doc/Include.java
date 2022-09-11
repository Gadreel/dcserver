package dcraft.script.inst.doc;

import dcraft.filestore.CommonPath;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.script.Script;
import dcraft.script.StackUtil;
import dcraft.script.inst.BlockInstruction;
import dcraft.script.work.BlockWork;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.ReturnOption;
import dcraft.struct.BaseStruct;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;
import dcraft.xml.XNode;

import java.util.Collection;

public class Include extends BlockInstruction {
	static public Include tag() {
		Include el = new Include();
		el.setName("dc.Include");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Include.tag();
	}

	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		if (state.getState() == ExecuteState.READY) {
			// remove the children
			this.clearChildren();

			if (this.hasAttribute("Target")) {
				BaseStruct xml = StackUtil.refFromSource(state, "Target", true);

				if (xml instanceof XElement) {
					// find position of this instruction
					IParentAwareWork pw = state.getParent();
					XElement pel = ((InstructionWork) pw).getInstruction();
					int pos = pel.findIndex(state.getInstruction());

					// after
					pel.add(pos + 1, (XNode) xml);

					//System.out.println("code: " + pel.toPrettyString());
				}
			}

			if (this.gotoTop(state))
				return ReturnOption.CONTINUE;
		}
		else {
			// each resume should go to next child instruction until we run out, then check logic
			if (! ((BlockWork) state).checkClearContinueFlag() && this.gotoNext(state, false))
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