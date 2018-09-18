package dcraft.db.proc;

import dcraft.db.ICallContext;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;

public interface IStoredProc {
	// false to cancel event - only works if this is a "before" trigger
	void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException;
}
