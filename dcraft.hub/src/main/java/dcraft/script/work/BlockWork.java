package dcraft.script.work;

import dcraft.hub.op.*;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.inst.Instruction;
import dcraft.struct.BaseStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.IParentAwareWork;
import dcraft.util.StringUtil;

import java.util.concurrent.atomic.AtomicBoolean;

public class BlockWork extends InstructionWork implements IParentAwareWork, IVariableProvider {
	static public BlockWork of(IParentAwareWork parent, Instruction inst) {
		BlockWork sw = new BlockWork();
		
		sw.parent = parent;
		sw.inst = inst;
		
		return sw;
	}
	
	protected RecordStruct variables = RecordStruct.record();
	protected InstructionWork currEntry = null;
	protected boolean controlaware = false;
	protected AtomicBoolean continueflag = new AtomicBoolean();
	
	protected BlockWork() { }
	
	public BlockWork withControlAware(boolean v) {
		this.controlaware = v;
		return this;
	}
	
	public boolean checkClearContinueFlag() {
		return this.continueflag.getAndSet(false);
	}
	
	// this is called from within the Script context, bypassing the std run ctx
	@Override
	public ReturnOption run() throws OperatingContextException {
		if ((inst == null) || (this.state == ExecuteState.DONE))
			return ReturnOption.DONE;
		
		while (true) {
			// continue
			if (this.currEntry != null) {
				ReturnOption eret = this.currEntry.run();
				
				// this means Function is done, not just the block
				if (eret == ReturnOption.DONE) {
					this.setState(ExecuteState.DONE);
					return eret;
				}
				
				if ((eret == ReturnOption.CONTROL_BREAK) || (eret == ReturnOption.CONTROL_CONTINUE)) {
					if (this.controlaware) {
						if (eret == ReturnOption.CONTROL_BREAK) {
							this.setState(ExecuteState.DONE);
							return ReturnOption.CONTINUE;
						}
						
						this.continueflag.set(true);
					}
					else {
						this.setState(ExecuteState.DONE);
						return eret;
					}
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
			
			// shouldn't really happen here, but if it does it applies upward
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
	
	public InstructionWork getCurrEntry() {
		return this.currEntry;
	}
	
	public void setCurrEntry(InstructionWork v) {
		this.currEntry = v;
	}
	
	@Override
	public void cancel() {
		if (this.currEntry != null)
			this.currEntry.cancel();
		
		super.cancel();
	}
	
	@Override
	public RecordStruct variables() {
		return this.variables;
	}
	
	@Override
	public void addVariable(String name, BaseStruct var) throws OperatingContextException {
		this.variables.with(name, var);
		
		if (var instanceof AutoCloseable) {
			OperationContext run = OperationContext.getOrThrow();
			
			if (run != null) {
				run.getController().addObserver(new OperationObserver() {
					@Override
					public void completed(OperationContext ctx) {
						try {
							((AutoCloseable) var).close();
						}
						catch (Exception x) {
							Logger.warn("Script could not close and autoclosable var: " + x);
						}
					}
				});
			}
		}
	}
	
	@Override
	public void clearVariables() {
		this.variables.clear();
	}
	
	@Override
	public BaseStruct queryVariable(String name) throws OperatingContextException {
		if (StringUtil.isEmpty(name))
			return null;
		
		if (this.variables.hasField(name)) {
			if (Logger.isTrace()) {
				Logger.trace("Found variable: " + name + " at " + this.inst.toLocalString());
			}

			return this.variables.getField(name);
		}

		IVariableAware va = StackUtil.queryVarAware(this.parent);

		if (va != null) {
			if (Logger.isTrace()) {
				Logger.trace("Parent found variable: " + name + " at " + this.inst.toLocalString());
			}

			return va.queryVariable(name);
		}

		return OperationContext.getOrThrow().queryVariable(name);
	}
}
