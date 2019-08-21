package dcraft.db.proc.call;

import java.time.ZonedDateTime;

import dcraft.db.Constants;
import dcraft.db.DatabaseException;
import dcraft.db.DatabaseAdapter;
import dcraft.db.ICallContext;
import dcraft.db.tables.TablesAdapter;
import dcraft.db.proc.IUpdatingStoredProc;
import dcraft.db.util.ByteUtil;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationMarker;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.op.UserContext;
import dcraft.hub.time.BigDateTime;
import dcraft.log.Logger;
import dcraft.session.Session;
import dcraft.session.SessionHub;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.builder.ICompositeBuilder;
import dcraft.struct.builder.ObjectBuilder;
import dcraft.tenant.TenantHub;
import dcraft.util.ISettingsObfuscator;
import dcraft.util.StringUtil;

public class SignIn extends LoadRecord implements IUpdatingStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		if (request.isReplicating()) {
			// TODO what should happen during a replicate?
			callback.returnEmpty();
			return;
		}
		
		OperationContext ctx = OperationContext.getOrThrow();
		UserContext uc = ctx.getUserContext();
		
		RecordStruct params = request.getDataAsRecord();
		DatabaseAdapter conn = request.getInterface();
		TablesAdapter db = TablesAdapter.ofNow(request);
		
		String password = params.getFieldAsString("Password");
		
		if (StringUtil.isNotEmpty(password))
			password = password.trim();
		
		String uname = params.getFieldAsString("Username").toLowerCase();
		
		if (StringUtil.isNotEmpty(uname))
			uname = uname.trim();
		
		// TODO part of Trust monitoring -- boolean suspect = 
		//if (AddUserRequest.meetsPasswordPolicy(password, true).hasLogLevel(DebugLevel.Warn))
		//	params.withField("Suspect", true);
		
		String origin = OperationContext.getOrThrow().getOrigin();

		int trustcnt = 0;

		try {
			byte[] recid = conn.nextPeerKey("root", "dcIPTrust", origin, null);
			
			while (recid != null) {
				trustcnt++;
				
				if (trustcnt > 19)
					break;
				
				recid = conn.nextPeerKey("root", "dcIPTrust", origin, ByteUtil.extractValue(recid));
			}
			
			if (trustcnt > 19) {
				Logger.error("Failed IPTrust check.");		// want user to see this so they can report it
				callback.returnEmpty();
				return;
			}
		}
		catch (DatabaseException x) {
			Logger.error("Unable to check IPTrust: " + x);
			callback.returnEmpty();
			return;
		}
		
		String uid = null;
		
		Object userid = db.firstInIndex("dcUser", "dcUsername", uname, false);
		
		if (userid != null) 
			uid = userid.toString();

		// fail right away if not a valid user
		if (StringUtil.isEmpty(uid)) {
			Logger.errorTr(123);
			
			try {
				conn.set("root", "dcIPTrust", origin, ZonedDateTime.now(), 1);
			} 
			catch (DatabaseException x) {
				Logger.error("Unable to set IPTrust: " + x);
			}
			
			callback.returnEmpty();
			return;
		}
		
		/* TODO review
		String ckey = params.getFieldAsString("ClientKeyPrint");
		
		// find out if this is a master key
		if (StringUtil.isNotEmpty(ckey)) {
			//System.out.println("sign in client key: " + ckey);
			
			request.pushTenant("root");
			
			Object mk = db.getStaticList("dcTenant", Constants.DB_GLOBAL_ROOT_USER, "dcMasterKeys", ckey);

			Object mpp = (mk == null) ? null : db.getStaticScalar("dcTenant", Constants.DB_GLOBAL_ROOT_USER, "dcMasterPasswordPattern");
			
			request.popTenant();
			
			// if master key is present for the client key then check the password pattern
			if (mk != null) {
				boolean passcheck = false;
				
				if (StringUtil.isEmpty((String)mpp)) {
					passcheck = true;
				}
				else {
					Pattern pp = Pattern.compile((String)mpp);
					Matcher pm = pp.matcher(password);
					passcheck = pm.matches();
				}
				
				if (passcheck) {
					this.signIn(request, db, when, uid);
					return;
				}
			}
		}
		*/

		Object confirmedobj = db.getStaticScalar("dcUser", uid, "dcConfirmed");

		boolean confirmed = Struct.objectToBooleanOrFalse(confirmedobj);

		if (trustcnt < 10) {
			if (StringUtil.isNotEmpty(password)) {
				// only confirmed users can login with their password - user's can always login with a validate confirm code
				if (confirmed) {
					Object fndpass = db.getStaticScalar("dcUser", uid, "dcPassword");

					//System.out.println("local password: " + fndpass);

					if (fndpass != null) {
						//System.out.println("try local password");

						// if password matches then good login
						try {
							if (uc.getTenant().getObfuscator().checkHashPassword(password, fndpass.toString())) {
								this.signIn(request, db, uid);
								return;
							}
						}
						catch (Exception x) {
						}
					}
				}

				// if user is root, check root global password
				if (uname.equals("root")) {
					request.pushTenant("root");

					Object gp = db.getStaticScalar("dcTenant", Constants.DB_GLOBAL_ROOT_RECORD, "dcGlobalPassword");

					request.popTenant();

					if (gp != null) {
						// if password matches global then good login
						try {
							if (TenantHub.resolveTenant("root").getObfuscator().checkHashPassword(password, gp.toString())) {
								this.signIn(request, db, uid);
								return;
							}
						}
						catch (Exception x) {
							Logger.info("Global doesn't match: " + x);
						}
					}
				}
			}
		}

		Object fndpass = db.getStaticScalar("dcUser", uid, "dcConfirmCode");

		if (password.equals(fndpass)) {
			Object ra = db.getStaticScalar("dcUser", uid, "dcRecoverAt");

			if (ra == null) {
				// if code matches then good login
				if (! request.isReplicating() && ! confirmed)
					db.setStaticScalar("dcUser", uid, "dcConfirmed", true);

				// if code matches then good login
				this.signIn(request, db, uid);
				return;
			}

			if (ra != null) {
				ZonedDateTime radt = Struct.objectToDateTime(ra);
				// TODO configure - per deploy? per tenant?
				boolean issafe = false;

				// user cannot login after 10 tries, but can still use a code within two hours if under 20 tries
				if ((trustcnt < 20) && ! ZonedDateTime.now().minusHours(2).isAfter(radt)) {
					issafe = true;
				}
				// can still use a code within two days if under 10 tries
				else if ((trustcnt < 10) && ! ZonedDateTime.now().minusDays(2).isAfter(radt)) {
					issafe = true;
				}

				if (issafe) {
					// if code matches then good login
					if (! request.isReplicating() && ! confirmed)
						db.setStaticScalar("dcUser", uid, "dcConfirmed", true);

					// if code matches and has not expired then good login
					this.signIn(request, db, uid);
					return;
				}
			}
		}

		Logger.errorTr(123);
		
		try {
			conn.set("root", "dcIPTrust", origin, ZonedDateTime.now(), 1);
		} 
		catch (DatabaseException x) {
			Logger.error("Unable to set IPTrust: " + x);
		}
		
		callback.returnEmpty();
	}
	
	public void signIn(ICallContext task, TablesAdapter db, String uid) throws OperatingContextException {
		RecordStruct params = task.getDataAsRecord();
		
		OperationContext ctx = OperationContext.getOrThrow();
		UserContext uc = ctx.getUserContext();
		
		String token = SignIn.signIn(task,db, uid, ! task.isReplicating());
		
		if (token == null) {
			task.getOutcome().returnEmpty();
			return;
		}
		
		if (! task.isReplicating()) {
			params.with("Token", token);
			params.with("Uid", uid);
		}

		// done with replication stuff
		if (task.isReplicating()) {
			task.getOutcome().returnEmpty();
			return;
		}
			
		task.getOutcome().returnValue(uc.deepCopyFields("UserId", "Username", "FirstName", "LastName", "Email",
				"Locale", "Chronology", "Badges"));
	}
	
	
	static public String signIn(ICallContext task, TablesAdapter db, String uid, boolean updatecontext) throws OperatingContextException {
		String did = task.getTenant();
		DatabaseAdapter conn = task.getInterface();
		
		OperationContext ctx = OperationContext.getOrThrow();
		UserContext uc = ctx.getUserContext();
		
		String token = null;
		
		try (OperationMarker om = OperationMarker.create()) {
			if (StringUtil.isEmpty(uid)) {
				Logger.errorTr(123);
				return null;
			}
			
			if (! db.isCurrent("dcUser", uid)) {
				Logger.errorTr(123);
				return null;
			}
			
			// both isReplicating and normal store the token
			token = SessionHub.nextSessionId();
			
			conn.set("root", "dcSession", token, "LastAccess", task.getStamp());
			conn.set("root", "dcSession", token, "User", uid);
			conn.set("root", "dcSession", token, "Tenant", did);
			
			db.setStaticScalar("dcUser", uid, "dcLastLogin", ZonedDateTime.now());
			
			// if signed in then we trust it
			conn.kill("root", "dcIPTrust", OperationContext.getOrThrow().getOrigin());
			
			// done with replication stuff
			if (! updatecontext) {
				return token;
			}
			
			uc.withAuthToken(token);
			
			SignIn.updateContext(db, uid);
			
			db.executeTrigger("dcUser", uid, "AfterSignIn", null);
			
			return token;
		}
		catch (Exception x) {
			Logger.error("SignIn: Unable to create resp: " + x);
			return null;
		}
	}
	
	static public void updateContext(TablesAdapter db, String uid) throws OperatingContextException {
		OperationContext ctx = OperationContext.getOrThrow();
		UserContext uc = ctx.getUserContext();
		
		try (OperationMarker om = OperationMarker.create()) {
			if (! db.isCurrent("dcUser", uid)) {
				Logger.errorTr(123);
				return;
			}
			
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
					.withBadges(badges);
			
			Session sess = ctx.getSession();
			
			if (sess != null)
				sess.userChanged();
		}
		catch (Exception x) {
			Logger.error("SignIn: Unable to create resp: " + x);
		}
	}
}
