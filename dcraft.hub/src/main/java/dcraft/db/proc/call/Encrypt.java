package dcraft.db.proc.call;

import dcraft.db.DbServiceRequest;
import dcraft.db.proc.IStoredProc;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.tenant.Tenant;

public class Encrypt implements IStoredProc {
	@Override
	public void execute(DbServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct params = request.getDataAsRecord();
		
		Tenant tenant = OperationContext.getOrThrow().getUserContext().getTenant();
		
		try {
			callback.returnValue(
					ListStruct.list().with(RecordStruct.record()
							.with("Value", tenant.getObfuscator().encryptStringToHex(params.getFieldAsString("Value")))
					)
			);
			
			return;
		}
		catch (Exception x) {
			Logger.error("Encrypt: Unable to create response: " + x);
		}
		
		callback.returnEmpty();
	}
}
