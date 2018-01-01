package dcraft.hub.op;

import dcraft.struct.RecordStruct;
import dcraft.util.cb.TimeoutPlan;

abstract public class OperationOutcomeRecord extends OperationOutcome<RecordStruct> {
	public OperationOutcomeRecord() throws OperatingContextException {
		super();
	}

	public OperationOutcomeRecord(TimeoutPlan plan) throws OperatingContextException {
		super(plan);
	}
}
