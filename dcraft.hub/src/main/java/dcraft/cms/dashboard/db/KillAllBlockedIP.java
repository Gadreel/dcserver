package dcraft.cms.dashboard.db;

import dcraft.db.DatabaseAdapter;
import dcraft.db.DatabaseException;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;

public class KillAllBlockedIP implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		try {
			DatabaseAdapter conn = request.getInterface();

			conn.kill("root", "dcIPTrust");
		}
		catch (DatabaseException x) {
			Logger.error("Unable to load IPTrust: " + x);
		}
		
		callback.returnEmpty();
	}
}
