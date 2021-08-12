package dcraft.core.db.user;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.RecordStruct;

public class UsernameLookup implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();
		
		String uname = data.getFieldAsString("Username").trim().toLowerCase();

		TablesAdapter db = TablesAdapter.of(request);
		
		Object userid = db.firstInIndex("dcUser", "dcUsername", uname, false);
		
		if (userid != null)
			callback.returnValue(RecordStruct.record().with("UserId", userid.toString()));
		else
			callback.returnEmpty();
	}
}
