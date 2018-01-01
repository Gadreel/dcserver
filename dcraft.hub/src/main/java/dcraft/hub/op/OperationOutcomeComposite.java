package dcraft.hub.op;

import dcraft.struct.CompositeStruct;
import dcraft.util.cb.TimeoutPlan;

abstract public class OperationOutcomeComposite extends OperationOutcome<CompositeStruct> {
	public OperationOutcomeComposite() throws OperatingContextException {
		super();
	}

	public OperationOutcomeComposite(TimeoutPlan plan) throws OperatingContextException {
		super(plan);
	}
}
