package dcraft.core.db.user;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.request.update.UpdateRecordRequest;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.op.UserContext;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;

public class SetAltPhone implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();
		
		String id = data.getFieldAsString("Id");

		TablesAdapter db = TablesAdapter.ofNow(request);

		if (! TableUtil.canWriteRecord(db, "dcUser", id, "dcCoreServices.Users.SetAltPhone", null, request.isFromRpc())) {
			Logger.error("Not permitted to update this record.");
			callback.returnEmpty();
			return;
		}

		RecordStruct phone = data.getFieldAsRecord("Phone");
	
		DbRecordRequest req = UpdateRecordRequest.update()
				.withTable("dcUser")
				.withId(data.getFieldAsString("Id"))
				.withUpdateField("dcAltPhones", phone.getFieldAsString("Stamp"), phone);
	
		TableUtil.updateRecord(db, req);
		
		callback.returnEmpty();
	}
}
