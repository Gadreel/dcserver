package dcraft.db.proc.call;

import dcraft.db.DatabaseAdapter;
import dcraft.db.DbServiceRequest;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationMarker;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.op.UserContext;
import dcraft.hub.time.BigDateTime;
import dcraft.log.Logger;
import dcraft.session.Session;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.builder.ICompositeBuilder;
import dcraft.struct.builder.ObjectBuilder;
import dcraft.util.StringUtil;

public class VerifySession extends LoadRecord {
	@Override
	public void execute(DbServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException {
		TablesAdapter db = TablesAdapter.of(request);
		String tenant = request.getTenant();
		BigDateTime when = BigDateTime.nowDateTime();
		
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
					Object username = db.getStaticScalar("dcUser", uid, "dcUsername");
					Object firstname = db.getStaticScalar("dcUser", uid, "dcFirstName");
					Object lastname = db.getStaticScalar("dcUser", uid, "dcLastName");
					Object email = db.getStaticScalar("dcUser", uid, "dcEmail");
					
					// always have User if signed in
					ListStruct badges = ListStruct.list("User");
					
					for (String sid : db.getStaticListKeys("dcUser", uid, "dcBadges"))
						badges.with(db.getStaticList("dcUser", uid, "dcBadges", sid));
					
					ListStruct locales = ListStruct.list();
					
					for (String sid : db.getStaticListKeys("dcUser", uid, "dcLocale"))
						locales.with(db.getStaticList("dcUser", uid, "dcLocale", sid));
					
					ListStruct chronos = ListStruct.list();
					
					for (String sid : db.getStaticListKeys("dcUser", uid, "dcChronology"))
						chronos.with(db.getStaticList("dcUser", uid, "dcChronology", sid));
					
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
		
		uc.clearToGuest();
		
		Session sess = ctx.getSession();
		
		if (sess != null)
			sess.userChanged();
		
		callback.returnEmpty();
	}
}
