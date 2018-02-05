package dcraft.db.proc;

import dcraft.db.ICallContext;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;

public interface IStoredProc {
	void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException;
}
