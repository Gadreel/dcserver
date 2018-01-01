package dcraft.hub.op;

import dcraft.struct.scalar.NullStruct;
import dcraft.util.cb.TimeoutPlan;

abstract public class OperationOutcomeEmpty extends OperationOutcome<NullStruct> {
	public OperationOutcomeEmpty() throws OperatingContextException {
		super();
	}

	public OperationOutcomeEmpty(TimeoutPlan plan) throws OperatingContextException {
		super(plan);
	}
	
	@Override
	public void callback(NullStruct result) throws OperatingContextException {
		this.callback();
	}
	
	public void returnResult() {
		super.returnValue(null);
	}
	
	abstract public void callback() throws OperatingContextException;
}
