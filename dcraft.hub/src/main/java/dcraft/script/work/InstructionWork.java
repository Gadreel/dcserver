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
package dcraft.script.work;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.inst.Instruction;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.IParentAwareWork;
import dcraft.task.TaskContext;

public class InstructionWork extends StackWork {
	static public InstructionWork of(IParentAwareWork parent, Instruction inst) {
		InstructionWork sw = new InstructionWork();
		
		sw.parent = parent;
		sw.inst = inst;
		
		return sw;
	}
	
	protected Instruction inst = null;
	
	protected InstructionWork() { }
	
	public Instruction getInstruction() {
		return this.inst;
	}
	
	public InstructionWork withInstruction(Instruction v) {
		this.inst = v;
		return this;
	}
	
	// this approach is if we want to run a single instruction out of the blue, not in a Script
	// the instruction can either issue an AWAIT - if so it must then issue a resume and, when done
	// must set the ExecuteState to Done. So if Done after an AWAIT then exit
	@Override
	public void run(TaskContext taskctx) throws OperatingContextException {
		if (this.state == ExecuteState.DONE)
			taskctx.returnResult();
		else if (this.run() != ReturnOption.AWAIT)
			taskctx.returnResult();
	}
	
	// this is called from within the Script context, bypassing the std run ctx above
	public ReturnOption run() throws OperatingContextException {
		if ((inst == null) || (this.state == ExecuteState.DONE))
			return ReturnOption.DONE;
		
		ReturnOption ret = this.inst.run(this);
		
		if (ret != ReturnOption.AWAIT)
			this.setState(ExecuteState.DONE);
		
		return ret;
	}
	
	// TODO enable ICancelAwareWork in TaskContext
	@Override
	public void cancel() {
		if (this.inst != null)
			this.inst.cancel(this);
	}
	
    @Override
    public void debugStack(ListStruct dumpList) {
    	RecordStruct dumpRec = new RecordStruct();
    	dumpList.withItem(dumpRec);
    	
    	this.collectDebugRecord(dumpRec);
    	RecordStruct subRec = this.inst.collectDebugRecord(this, dumpRec);

    	if (subRec != null)
    		dumpList.withItem(subRec);
    }
    
    @Override
    public void collectDebugRecord(RecordStruct rec) {
    }
}
