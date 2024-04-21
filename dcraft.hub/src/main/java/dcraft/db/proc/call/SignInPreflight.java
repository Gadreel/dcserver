package dcraft.db.proc.call;

import dcraft.db.Constants;
import dcraft.db.DatabaseAdapter;
import dcraft.db.DatabaseException;
import dcraft.db.ICallContext;
import dcraft.db.proc.IUpdatingStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.db.util.ByteUtil;
import dcraft.hub.op.*;
import dcraft.log.Logger;
import dcraft.session.Session;
import dcraft.session.SessionHub;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.tenant.TenantHub;
import dcraft.util.StringUtil;

import java.time.ZonedDateTime;

public class SignInPreflight extends LoadRecord implements IUpdatingStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		if (request.isReplicating()) {
			// TODO what should happen during a replicate?
			callback.returnEmpty();
			return;
		}

		// TODO eventually support various MFA

		callback.returnValue(RecordStruct.record().with("Handler", "Standard"));
	}
}
