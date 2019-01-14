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
import dcraft.script.work.InstructionWork;
import dcraft.script.work.ReturnOption;
import dcraft.struct.RecordStruct;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

/*
	make sure this - and all children - is immutable in regards to
	run/execute/cancel - no member vars specific to the stack
 */
abstract public class Instruction extends XElement {
    abstract public ReturnOption run(InstructionWork state) throws OperatingContextException;
	
    // override this if your instruction can cancel...
    public void cancel(InstructionWork state) { }
    
	public RecordStruct collectDebugRecord(InstructionWork state, RecordStruct rec) {
		rec.with("Line", this.getLine());
		rec.with("Column", this.getCol());
	   	rec.with("Command", this.toLocalString());
	   	
	   	return null;
	}

	public InstructionWork createStack(IParentAwareWork state) {
		return InstructionWork.of(state, this);
	}
	
	public InstructionWork createStackMain(IParentAwareWork state) throws Exception {
    	throw new Exception(this.toLocalString() + ": instruction cannot be script main");
	}
}
