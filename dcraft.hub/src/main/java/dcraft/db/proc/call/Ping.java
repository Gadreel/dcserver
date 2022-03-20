package dcraft.db.proc.call;

import dcraft.db.proc.IStoredProc;
import dcraft.db.ICallContext;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.scalar.StringStruct;

public class Ping implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		try {
			Logger.info("Go a ping request, replying.");

			callback.returnValue(StringStruct.of("Pong"));
			
			return;
		}
		catch (Exception x) {
			Logger.error("Ping: Unable to create response: " + x);
		}
		
		callback.returnEmpty();
	}
}
