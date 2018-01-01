package dcraft.db.proc;

import dcraft.db.DbServiceRequest;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;

public interface IStoredProc {
	void execute(DbServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException;
}
