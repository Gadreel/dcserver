package dcraft.db.proc.call;

import dcraft.db.DbServiceRequest;
import dcraft.db.proc.IStoredProc;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;

public class Echo implements IStoredProc {
	@Override
	public void execute(DbServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException {
		try {
			callback.returnValue(request.getData());
			
			return;
		}
		catch (Exception x) {
			Logger.error("Echo: Unable to create response: " + x);
		}
		
		callback.returnEmpty();
	}
}
