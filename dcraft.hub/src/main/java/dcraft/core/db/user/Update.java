package dcraft.core.db.user;

import dcraft.core.db.UserDataUtil;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationMarker;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;

public class Update implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		String id = data.getFieldAsString("Id");

		TablesAdapter db = TablesAdapter.of(request);

		if (! TableUtil.canWriteRecord(db, "dcUser", id, "dcCoreServices.Users.Update", null, request.isFromRpc())) {
			Logger.error("Not permitted to update this record.");
			callback.returnEmpty();
			return;
		}

		try (OperationMarker om = OperationMarker.create()) {
			DbRecordRequest req = UserDataUtil.updateUserWithConditions(data);
			
			if (! om.hasErrors())
				TableUtil.updateRecord(db, req);
		}
		
		callback.returnEmpty();
	}
}
