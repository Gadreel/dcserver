package dcraft.script.inst;

import dcraft.script.work.InstructionWork;
import dcraft.script.work.BlockWork;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XNode;

abstract public class BlockInstruction extends Instruction {
	
	// use orTop first call or if this is a loop that should repeat
	public boolean gotoNext(InstructionWork state, boolean orTop) {
		BlockWork blockWork = (BlockWork) state;
		
		if (this.children == null)
			return false;
		
		InstructionWork curr = blockWork.getCurrEntry();
		
		if ((curr == null) && ! orTop)
			return false;
		
		if (curr == null)
			return this.gotoTop(state);
		
		boolean fnd = false;
		
		for (XNode n : this.children) {
			if (n == curr.getInstruction()) {
				fnd = true;
				continue;
			}
			
			if (! fnd)
				continue;
			
			if (n instanceof Instruction) {
				blockWork.setCurrEntry(((Instruction) n).createStack(state));
				return true;
			}
		}
		
		return false;
	}
	
	public boolean gotoTop(InstructionWork state) {
		BlockWork blockWork = (BlockWork) state;
		
		if (this.children == null)
			return false;
		
		for (XNode n : this.children) {
			if (n instanceof Instruction) {
				blockWork.setCurrEntry(((Instruction) n).createStack(state));
				return true;
			}
		}
		
		return false;
	}
	
	@Override
	public InstructionWork createStack(IParentAwareWork state) {
		return BlockWork.of(state, this);
	}
}
