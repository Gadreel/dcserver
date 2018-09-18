package dcraft.db.proc;

import dcraft.hub.op.*;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.inst.Instruction;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.ReturnOption;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.IParentAwareWork;
import dcraft.util.StringUtil;

import java.util.concurrent.atomic.AtomicBoolean;

public class RecordScope implements IVariableProvider {
	static public RecordScope of(IVariableAware parent) {
		RecordScope sw = new RecordScope();

		sw.parent = parent;

		return sw;
	}

	protected IVariableAware parent = null;
	protected RecordStruct variables = RecordStruct.record()
			.with("_RecordCache", RecordStruct.record());

	protected RecordScope() { }

	@Override
	public RecordStruct variables() {
		return this.variables;
	}
	
	@Override
	public void addVariable(String name, Struct var) throws OperatingContextException {
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
	public Struct queryVariable(String name) throws OperatingContextException {
		if (StringUtil.isEmpty(name))
			return null;
		
		if (this.variables.hasField(name))
			return this.variables.getField(name);

		if (this.parent != null)
			return this.parent.queryVariable(name);

		return OperationContext.getOrThrow().queryVariable(name);
	}
}
