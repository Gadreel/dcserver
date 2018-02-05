package dcraft.db.proc.call;

import dcraft.db.proc.IStoredProc;
import dcraft.db.ICallContext;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;

public class Echo implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
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
