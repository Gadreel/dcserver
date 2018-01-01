package dcraft.hub.op;

import dcraft.util.cb.TimeoutPlan;

abstract public class OperationOutcomeInteger extends OperationOutcome<Integer> {
	public OperationOutcomeInteger() throws OperatingContextException {
		super();
	}

	public OperationOutcomeInteger(TimeoutPlan plan) throws OperatingContextException {
		super(plan);
	}
}
