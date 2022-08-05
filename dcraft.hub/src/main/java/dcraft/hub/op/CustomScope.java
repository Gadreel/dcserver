package dcraft.hub.op;

import dcraft.log.Logger;
import dcraft.struct.BaseStruct;
import dcraft.struct.RecordStruct;
import dcraft.util.StringUtil;

public class CustomScope implements IVariableProvider {
	static public CustomScope of(IVariableAware parent) {
		CustomScope sw = new CustomScope();

		sw.parent = parent;

		return sw;
	}

	protected IVariableAware parent = null;
	protected RecordStruct variables = RecordStruct.record();

	protected CustomScope() { }

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
		
		if (this.variables.hasField(name))
			return this.variables.getField(name);

		if (this.parent != null)
			return this.parent.queryVariable(name);

		return OperationContext.getOrThrow().queryVariable(name);
	}
}
