package dcraft.db.proc.call;

import dcraft.db.DbServiceRequest;
import dcraft.db.proc.IStoredProc;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.scalar.StringStruct;

public class Ping implements IStoredProc {
	@Override
	public void execute(DbServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException {
		try {
			callback.returnValue(StringStruct.of("Pong"));
			
			return;
		}
		catch (Exception x) {
			Logger.error("Ping: Unable to create response: " + x);
		}
		
		callback.returnEmpty();
	}
}
