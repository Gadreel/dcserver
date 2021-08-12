package dcraft.core.db.user;

import dcraft.core.db.UserDataUtil;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.RecordStruct;

public class Add implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		TablesAdapter db = TablesAdapter.of(request);
		
		DbRecordRequest req = UserDataUtil.addUserWithConditions(data, data.hasField("Confirmed") ? data.getFieldAsBooleanOrFalse("Confirmed") : true);
		
		// TODO db trigger or script event to elaborate?
		
		String newid = TableUtil.updateRecord(db, req);

		callback.returnValue(
				RecordStruct.record()
					.with("Id", newid)
		);
	}
}
