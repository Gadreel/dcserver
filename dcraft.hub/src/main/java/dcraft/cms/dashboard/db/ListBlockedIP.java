package dcraft.cms.dashboard.db;

import dcraft.db.DatabaseAdapter;
import dcraft.db.DatabaseException;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.util.ByteUtil;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;

import java.time.ZonedDateTime;

public class ListBlockedIP implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		try {
			ListStruct blocked = ListStruct.list();

			DatabaseAdapter conn = request.getInterface();

			byte[] addr = conn.nextPeerKey("root", "dcIPTrust", null);

			while (addr != null) {
				Object origin = ByteUtil.extractValue(addr);

				byte[] lasttime = conn.nextPeerKey("root", "dcIPTrust", origin, null);
				ZonedDateTime last = null;
				int trustcnt = 0;

				while (lasttime != null) {
					last = Struct.objectToDateTime(ByteUtil.extractValue(lasttime));

					trustcnt++;

					lasttime = conn.nextPeerKey("root", "dcIPTrust", origin, last);
				}

				blocked.with(RecordStruct.record()
						.with("Address", origin)
						.with("Last", last)
						.with("Count", trustcnt)
				);

				addr = conn.nextPeerKey("root", "dcIPTrust", origin);
			}

			callback.returnValue(blocked);
		}
		catch (DatabaseException x) {
			Logger.error("Unable to load IPTrust: " + x);
			callback.returnEmpty();
		}
	}
}
