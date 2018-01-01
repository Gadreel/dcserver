package dcraft.hub.op;

import dcraft.struct.ListStruct;
import dcraft.util.cb.TimeoutPlan;

abstract public class OperationOutcomeList extends OperationOutcome<ListStruct> {
	public OperationOutcomeList() throws OperatingContextException {
		super();
	}

	public OperationOutcomeList(TimeoutPlan plan) throws OperatingContextException {
		super(plan);
	}
}
