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

public class SetReference implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();
		
		String id = data.getFieldAsString("Id");

		TablesAdapter db = TablesAdapter.ofNow(request);

		if (! TableUtil.canWriteRecord(db, "dcUser", id, "dcCoreServices.Users.SetReference", null, request.isFromRpc())) {
			Logger.error("Not permitted to update this record.");
			callback.returnEmpty();
			return;
		}

		RecordStruct reference = data.getFieldAsRecord("Reference");
		
		DbRecordRequest req = UpdateRecordRequest.update()
				.withTable("dcUser")
				.withId(data.getFieldAsString("Id"))
				.withUpdateField("dcReference", reference.getFieldAsString("Stamp"), reference);
	
		TableUtil.updateRecord(db, req);
		
		callback.returnEmpty();
	}
}
