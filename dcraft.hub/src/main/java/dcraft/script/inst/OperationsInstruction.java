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

import dcraft.script.work.InstructionWork;
import dcraft.script.work.OperationWork;
import dcraft.script.work.OperationsWork;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;
import dcraft.xml.XNode;

// for instructions that are composed of multiple operations
abstract public class OperationsInstruction extends Instruction {
	// use orTop first call or if this is a loop that should repeat
	public boolean gotoNext(InstructionWork state, boolean orTop) {
		OperationsWork opsWork = (OperationsWork) state;
		
		OperationWork curr = opsWork.getCurrEntry();
		
		if ((curr == null) && ! orTop)
			return false;
		
		if (curr == null)
			return this.gotoTop(state);
		
		if (this.children == null)
			return false;
		
		boolean fnd = false;
		
		for (XNode n : this.children) {
			if (n == curr.getOperation()) {
				fnd = true;
				continue;
			}
			
			if (! fnd)
				continue;
			
			if (n instanceof XElement) {
				opsWork.setCurrEntry(OperationWork.of(state, (XElement) n));
				return true;
			}
		}
		
		return false;
	}
	
	public boolean gotoTop(InstructionWork state) {
		OperationsWork blockWork = (OperationsWork) state;
		
		if (this.children == null)
			return false;
		
		for (XNode n : this.children) {
			if (n instanceof XElement) {
				blockWork.setCurrEntry(OperationWork.of(state, (XElement) n));
				return true;
			}
		}
		
		return false;
	}
	
	@Override
	public InstructionWork createStack(IParentAwareWork state) {
		return OperationsWork.of(state, this);
	}
}
