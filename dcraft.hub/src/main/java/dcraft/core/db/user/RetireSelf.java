package dcraft.core.db.user;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.proc.call.SignOut;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.service.ServiceHub;
import dcraft.service.ServiceRequest;
import dcraft.struct.RecordStruct;

public class RetireSelf implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		TablesAdapter db = TablesAdapter.ofNow(request);
		
		TableUtil.retireRecord(db, "dcUser", data.getFieldAsString("Id"));
		
		SignOut.signout(request);
		
		callback.returnEmpty();
	}
}
