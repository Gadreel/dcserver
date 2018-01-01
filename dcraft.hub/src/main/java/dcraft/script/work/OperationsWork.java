package dcraft.script.work;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.script.inst.Instruction;
import dcraft.struct.Struct;
import dcraft.task.IParentAwareWork;

public class OperationsWork extends InstructionWork {
	static public OperationsWork of(IParentAwareWork parent, Instruction inst) {
		OperationsWork sw = new OperationsWork();
		
		sw.parent = parent;
		sw.inst = inst;
		
		return sw;
	}
	
	protected OperationWork currEntry = null;
	protected Struct target = null;
	
	protected OperationsWork() { }
	
	public Struct getTarget() {
		return this.target;
	}
	
	public void setTarget(Struct v) {
		this.target = v;
	}
	
	// this is called from within the Script context, bypassing the std run ctx
	@Override
	public ReturnOption run() throws OperatingContextException {
		if ((inst == null) || (this.state == ExecuteState.DONE))
			return ReturnOption.DONE;

		while (true) {
			// continue
			if (this.currEntry != null) {
				ReturnOption eret = this.currEntry.run(this.target);
				
				// this means Function is done, not just the block
				if (eret == ReturnOption.DONE) {
					this.setState(ExecuteState.DONE);
					return eret;
				}
				
				if ((eret == ReturnOption.CONTROL_BREAK) || (eret == ReturnOption.CONTROL_CONTINUE)) {
					this.setState(ExecuteState.DONE);
					return eret;
				}
				
				if (eret == ReturnOption.AWAIT)
					return eret;
				
				// always await when debugging - actually align instruction, then AWAIT
				//if (OperationContext.getAsTaskOrThrow().hasDebugger())
				//	return ReturnOption.AWAIT;
			}
			
			// if instruction wants to issue an AWAIT it must clear the currEntry - otherwise the AWAIT
			// is treated as for the entry and not the instruction
			ReturnOption ret = this.inst.run(this);

			// that which returns AWAIT must set RESUME in state
			if (ret == ReturnOption.AWAIT)
				return ret;

			// DONE for a block instruction just means Done with Logic, no more loop/children
			if (ret == ReturnOption.DONE)
				this.setState(ExecuteState.DONE);
			
			if (this.state == ExecuteState.DONE) {
				// always await when debugging
				if (OperationContext.getAsTaskOrThrow().hasDebugger())
					return ReturnOption.AWAIT;
				
				return ReturnOption.CONTINUE;
			}
			
			if ((ret == ReturnOption.CONTROL_BREAK) || (ret == ReturnOption.CONTROL_CONTINUE)) {
				this.setState(ExecuteState.DONE);
				return ret;
			}
			
			// mark we have run once
			if (this.state == ExecuteState.READY)
				this.setState(ExecuteState.RESUME);

			// always await when debugging
			if (OperationContext.getAsTaskOrThrow().hasDebugger())
				return ReturnOption.AWAIT;
		}
	}
	
	public OperationWork getCurrEntry() {
		return this.currEntry;
	}
	
	public void setCurrEntry(OperationWork v) {
		this.currEntry = v;
	}
	
	@Override
	public void cancel() {
		if (this.currEntry != null)
			this.currEntry.cancel();
		
		super.cancel();
	}
}
