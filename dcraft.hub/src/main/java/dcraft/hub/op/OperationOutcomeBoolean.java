package dcraft.hub.op;

import dcraft.util.cb.TimeoutPlan;

abstract public class OperationOutcomeBoolean extends OperationOutcome<Boolean> {
	public OperationOutcomeBoolean() throws OperatingContextException {
		super();
	}

	public OperationOutcomeBoolean(TimeoutPlan plan) throws OperatingContextException {
		super(plan);
	}
}
