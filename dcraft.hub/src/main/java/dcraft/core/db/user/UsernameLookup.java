package dcraft.core.db.user;

import dcraft.core.db.UserDataUtil;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.interchange.google.RecaptchaUtil;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class UsernameLookup implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();
		
		String uname = data.getFieldAsString("Username").trim().toLowerCase();

		TablesAdapter db = TablesAdapter.ofNow(request);
		
		Object userid = db.firstInIndex("dcUser", "dcUsername", uname, false);
		
		if (userid != null)
			callback.returnValue(RecordStruct.record().with("UserId", userid.toString()));
		else
			callback.returnEmpty();
	}
}
