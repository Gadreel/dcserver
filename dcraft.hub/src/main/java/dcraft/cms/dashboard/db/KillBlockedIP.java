package dcraft.cms.dashboard.db;

import dcraft.db.DatabaseAdapter;
import dcraft.db.DatabaseException;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.util.ByteUtil;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;

import java.time.ZonedDateTime;

public class KillBlockedIP implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		try {
			ListStruct blocked = request.getDataAsList();

			DatabaseAdapter conn = request.getInterface();

			for (int i = 0; i < blocked.size(); i++) {
				Object origin = blocked.getAt(i);

				conn.kill("root", "dcIPTrust", origin);
			}
		}
		catch (DatabaseException x) {
			Logger.error("Unable to load IPTrust: " + x);
		}

		callback.returnEmpty();
	}
}
