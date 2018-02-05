package dcraft.db.proc.call;

import java.time.ZonedDateTime;

import dcraft.db.ICallContext;
import dcraft.db.tables.TablesAdapter;
import dcraft.db.proc.IUpdatingStoredProc;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.time.BigDateTime;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.util.StringUtil;

public class InitiateConfirm implements IUpdatingStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		TablesAdapter db = TablesAdapter.ofNow(request);
		BigDateTime when = BigDateTime.nowDateTime();
		
		RecordStruct params = request.getDataAsRecord();
		String user = params.getFieldAsString("Username").trim().toLowerCase();		// switch to indexed value

		try {			
			if (request.isReplicating()) {
				// TODO
			}
			else {
				boolean uisemail = false;
				Object userid = db.firstInIndex("dcUser", "dcUsername", user);
				
				if (userid == null) {
					userid = db.firstInIndex("dcUser", "dcEmail", user);
					uisemail = true;		// true for email or backup email
				}
				
				if (userid == null)	
					userid = db.firstInIndex("dcUser", "dcBackupEmail", user);
				
				if (userid == null) {
					Logger.error("Unable to complete recovery");
					callback.returnEmpty();
					return;
				}

				String uid = userid.toString();
				String code = StringUtil.buildSecurityCode();
				
				db.setStaticScalar("dcUser", uid, "dcConfirmCode", code);
				db.setStaticScalar("dcUser", uid, "dcRecoverAt", ZonedDateTime.now());
				
				//String email = uisemail ? uid : (String) db.getStaticScalar("dcUser", uid, "dcEmail");
				
				callback.returnValue(RecordStruct.record()
							.with("Id", uid)
							.with("Code", code)		// TODO review this, prefer generalize and to not send this - defeats security :(
				);
				
				return;
			}
		}
		catch (Exception x) {
			Logger.error("Account Recovery: Unable to create resp: " + x);
		}
		
		callback.returnEmpty();
	}
}
