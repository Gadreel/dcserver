package dcraft.hub.op;

import dcraft.util.cb.TimeoutPlan;

abstract public class OperationOutcomeString extends OperationOutcome<String> {
	public OperationOutcomeString() throws OperatingContextException {
		super();
	}

	public OperationOutcomeString(TimeoutPlan plan) throws OperatingContextException {
		super(plan);
	}
}
