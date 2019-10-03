package dcraft.core.db.user;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.request.query.SelectFields;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.op.UserContext;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;

public class LoadEducation implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		String id = data.getFieldAsString("Id");

		TablesAdapter db = TablesAdapter.ofNow(request);

		if (! TableUtil.canReadRecord(db, "dcUser", id, "dcCoreServices.Users.LoadEducation", null, request.isFromRpc())) {
			Logger.error("Not permitted to load this record.");
			callback.returnEmpty();
			return;
		}

		RecordStruct user = TableUtil.getRecord(db, OperationContext.getOrThrow(), "dcUser", id, SelectFields.select().with("dcEducation"));

		callback.returnValue(user.getFieldAsList("dcEducation"));
	}
}
