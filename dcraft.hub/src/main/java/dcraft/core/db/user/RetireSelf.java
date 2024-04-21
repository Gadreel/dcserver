package dcraft.core.db.user;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.proc.call.SignOut;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.RecordStruct;

public class RetireSelf implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		TablesAdapter db = TablesAdapter.of(request);
		
		TableUtil.retireRecord(db, "dcUser", OperationContext.getOrThrow().getUserContext().getUserId());
		
		SignOut.signout(request);
		
		callback.returnEmpty();
	}
}
