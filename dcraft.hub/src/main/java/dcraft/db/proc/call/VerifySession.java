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
					
					// load the local user context
					Object username = db.getScalar("dcUser", uid, "dcUsername");
					Object firstname = db.getScalar("dcUser", uid, "dcFirstName");
					Object lastname = db.getScalar("dcUser", uid, "dcLastName");
					Object email = db.getScalar("dcUser", uid, "dcEmail");
					
					// always have User if signed in
					ListStruct badges = ListStruct.list("User");
					
					for (String sid : db.getListKeys("dcUser", uid, "dcBadges"))
						badges.with(db.getList("dcUser", uid, "dcBadges", sid));
					
					ListStruct locales = ListStruct.list();
					
					for (String sid : db.getListKeys("dcUser", uid, "dcLocale"))
						locales.with(db.getList("dcUser", uid, "dcLocale", sid));
					
					ListStruct chronos = ListStruct.list();
					
					for (String sid : db.getListKeys("dcUser", uid, "dcChronology"))
						chronos.with(db.getList("dcUser", uid, "dcChronology", sid));
					
					uc
							.withUserId(uid)
							.withUsername(username.toString())
							.withFirstName(firstname != null ? firstname.toString() : null)
							.withLastName(lastname != null ? lastname.toString() : null)
							.withEmail(email != null ? email.toString() : null)
							.withLocale(locales)
							.withChronology(chronos)
							.withBadges(badges)
							.withAuthToken(token);
					
					Session sess = ctx.getSession();
					
					if (sess != null)
						sess.userChanged();
					
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
