package dcraft.db.proc.call;

import dcraft.db.DatabaseAdapter;
import dcraft.db.ICallContext;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationMarker;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.op.UserContext;
import dcraft.log.Logger;
import dcraft.session.Session;
import dcraft.struct.ListStruct;
import dcraft.util.StringUtil;

public class VerifySession extends LoadRecord {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		TablesAdapter db = TablesAdapter.of(request);
		String tenant = request.getTenant();
		
		OperationContext ctx = OperationContext.getOrThrow();
		UserContext uc = ctx.getUserContext();
		String token = uc.getAuthToken();
		
		DatabaseAdapter conn = request.getInterface();

		try (OperationMarker om = OperationMarker.create()) {
			if (StringUtil.isEmpty(token))
				Logger.errorTr(117);
			else {
				String dd = (String) conn.get("root", "dcSession", token, "Tenant");
				String uid = (String) conn.get("root", "dcSession", token, "User");
				
				if (! tenant.equals(dd) || StringUtil.isEmpty(uid)) {
					Logger.errorTr(121);
				}
				else {					
					conn.set("root", "dcSession", token, "LastAccess", request.getStamp());

					SignIn.updateContext(db, uid);

					callback.returnEmpty();
					return;
				}
			}
		}
		catch (Exception x) {
			Logger.error("SignOut: Unable to create resp: " + x);
		}
		
		uc.clearToGuestKeepSite();
		
		Session sess = ctx.getSession();
		
		if (sess != null)
			sess.userChanged();
		
		callback.returnEmpty();
	}
}
