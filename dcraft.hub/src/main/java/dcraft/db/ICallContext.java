package dcraft.db;

import dcraft.db.DatabaseAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.service.ServiceRequest;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;

import java.math.BigDecimal;

public interface ICallContext extends IRequestContext {
	OperationOutcomeStruct getOutcome();
}
