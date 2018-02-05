package dcraft.db.proc.call;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import dcraft.db.DatabaseAdapter;
import dcraft.db.proc.IUpdatingStoredProc;
import dcraft.db.ICallContext;
import dcraft.db.util.ByteUtil;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;

public class Cleanup implements IUpdatingStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct params = request.getDataAsRecord();
		//DateTime expire = params.getFieldAsDateTime("ExpireThreshold");
		ZonedDateTime lexpire = params.getFieldAsDateTime("LongExpireThreshold");
		
		DatabaseAdapter conn = request.getInterface();

		try {
			int cnt = 0;
			byte[] sessonid = conn.nextPeerKey("root", "dcSession", null);

			while (sessonid != null) { 
				String token = ByteUtil.extractValue(sessonid).toString();
				
				BigDecimal la = conn.getAsDecimal("root", "dcSession", token, "LastAccess");
				
				if ((la == null) || (lexpire.toInstant().toEpochMilli() > la.abs().longValue())) {
					conn.kill("root", "dcSession", token);
					cnt++;
				}
				
				sessonid = conn.nextPeerKey("root", "dcSession", token);
			}
			
			Logger.info("Sessions cleaned: " + cnt);
		}
		catch (Exception x) {
			Logger.error("SignOut: Unable to create resp: " + x);
		}
		
		callback.returnEmpty();
	}
}
