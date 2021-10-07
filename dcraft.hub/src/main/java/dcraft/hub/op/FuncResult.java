package dcraft.hub.op;

import dcraft.struct.Struct;

public class FuncResult<T> extends OperationMarker {
	protected T value = null;

	public T getResult() {
	    return this.value;
	}
		 
	public void setResult(T v) {
	    this.value = v;
	}
	
	public boolean isNotEmptyResult() {
		return ! Struct.objectIsEmpty(this.value);
	}
	
	public boolean isEmptyResult() {
		return Struct.objectIsEmpty(this.value);
	}

	public FuncResult() throws OperatingContextException {
		OperationContext ctx = OperationContext.getOrThrow();
		this.contextid = ctx.getOpId();
		this.msgStart = ctx.getController().logMarker();
	}
}
