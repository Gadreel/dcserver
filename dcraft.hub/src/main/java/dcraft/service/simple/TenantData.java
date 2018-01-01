package dcraft.service.simple;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dcraft.db.Constants;
import dcraft.db.request.common.RequestFactory;
import dcraft.db.request.query.LoadRecordRequest;
import dcraft.db.request.query.SelectFields;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.op.UserContext;
import dcraft.log.Logger;
import dcraft.service.ServiceHub;
import dcraft.session.Session;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.tenant.Tenant;
import dcraft.tenant.work.PrepWork;
import dcraft.util.HexUtil;
import dcraft.util.RndUtil;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;
import dcraft.xml.XmlReader;

public class TenantData {
	static public TenantData of(Tenant tenant) {
		if (tenant == null)
			return null;
		
		TenantData du = new TenantData();
		
		du.tenantalias = tenant.getAlias();
		
		for (XElement usr : tenant.getResources().getConfig().getTagListDeep("Users/User"))
			du.addUser(usr);
		
		// if not using database and no root user is present then add one
		if (! ResourceHub.getResources().getDatabases().hasDefaultDatabase() && ! du.cachedIndex.containsKey("root")) {
			XElement usr = XElement.tag("User")
					.withAttribute("Id", "0")
					.withAttribute("Username", "root")
					.withAttribute("First", "Root")
					.withAttribute("Last", "User")
					.withAttribute("Email", "root@locahost")
					.withAttribute("PlainPassword", "A1s2d3f4")
					.with(XElement.tag("Badge").withText("Admin"))
					.with(XElement.tag("Badge").withText("Developer"))
					.with(XElement.tag("Badge").withText("SysAdmin"));
			
			du.addUser(usr);
		}

		return du;
	}
	
	protected String tenantalias = null;
	protected Map<String, XElement> cachedIndex = new HashMap<>();
	
	// token to username map
	protected Map<String, String> authtokens = new HashMap<>();
	
	public void verifyToken(String authtoken, OperationOutcomeStruct callback) throws OperatingContextException {
		if (StringUtil.isEmpty(authtoken)) {
			Logger.errorTr(442);
			Logger.error("AuthToken missing, could not verify");
			this.clear(authtoken, callback);
			return;
		}
		
		if (ResourceHub.getResources().getDatabases().hasDefaultDatabase()) {
			ServiceHub.call(RequestFactory.verifySessionRequest()
					.toServiceRequest()
					.withOutcome(callback)
			);
		}
		else {
			String uname = this.authtokens.get(authtoken);
			
			if (StringUtil.isEmpty(uname)) {
				Logger.errorTr(442);
				Logger.error("AuthToken not found, could not verify");
				this.clear(authtoken, callback);
				return;
			}
			
			XElement usr = this.cachedIndex.get(uname);
			
			if (usr == null) {
				Logger.errorTr(442);
				Logger.error("User not found, could not verify");
				this.clear(null, callback);
				return;
			}
			
			this.updateContext(usr, uname, authtoken);
			
			callback.returnEmpty();
		}
	}
	
	public void verifyCreds(String username, String password, OperationOutcomeStruct callback) throws OperatingContextException {
		if (ResourceHub.getResources().getDatabases().hasDefaultDatabase()) {
			ServiceHub.call(RequestFactory.signInRequest(username, password, null)
					.toServiceRequest()
					.withOutcome(callback)
			);
		}
		else {
			XElement usr = this.cachedIndex.get(username);
			
			if (usr == null) {
				Logger.errorTr(442);
				Logger.error("User not found, could not verify");
				this.clear(null, callback);
				return;
			}
			
			String upass = usr.getAttribute("Password");
			
			// any setting in the config file is set with Hub crypto not tenant crypto
			if (!ApplicationHub.getClock().getObfuscator().checkHashPassword(password, upass)) {
				Logger.errorTr(442);
				Logger.error("Invalid credentials, could not verify");
				this.clear(null, callback);
				return;
			}
			
			byte[] feedbuff = new byte[32];
			RndUtil.random.nextBytes(feedbuff);
			String token = HexUtil.bufferToHex(feedbuff);
			
			this.updateContext(usr, username, token);
			
			callback.returnValue(this.info(username));
		}
	}
	
	public void clear(String token, OperationOutcomeStruct callback) throws OperatingContextException {
		if (ResourceHub.getResources().getDatabases().hasDefaultDatabase()) {
			ServiceHub.call(RequestFactory.signOutRequest()
					.toServiceRequest()
					.withOutcome(callback)
			);
		}
		else {
			if (StringUtil.isNotEmpty(token))
				this.authtokens.remove(token);
			
			OperationContext ctx = OperationContext.getOrThrow();
			UserContext uc = ctx.getUserContext();
			
			uc.clearToGuestKeepSite();
			
			Session sess = ctx.getSession();
			
			if (sess != null)
				sess.userChanged();
			
			callback.returnEmpty();
		}
	}
	
	public void updateContext(XElement usr, String username, String token) throws OperatingContextException {
		String uid = usr.getAttribute("Id");
		
		List<XElement> tags = usr.selectAll("Badge");
		
		String[] atags = new String[tags.size() + 1];
		
		atags[0] = "User";
		
		for (int i = 1; i < atags.length; i++) 
			atags[i] = tags.get(i - 1).getText();
		
		OperationContext ctx = OperationContext.getOrThrow();
		UserContext uc = ctx.getUserContext();
		
		uc
			.withUserId(uid)
			.withUsername(usr.getAttribute("Username"))
			.withFirstName(usr.getAttribute("FirstName"))
			.withLastName(usr.getAttribute("LastName"))
			.withEmail(usr.getAttribute("Email"))
			.withBadges(atags)
			.withAuthToken(token);
		
		Session sess = ctx.getSession();
		
		if (sess != null)
			sess.userChanged();
		
		this.authtokens.put(token, username);
	}
	
	public RecordStruct info(String username) {
		XElement usr = this.cachedIndex.get(username);
		
		if (usr == null) 
			return null;
		
		return RecordStruct.record()
			.with("Username", usr.getAttribute("Username"))
			.with("FirstName", usr.getAttribute("First"))
			.with("LastName", usr.getAttribute("Last"))
			.with("Email", usr.getAttribute("Email"));
	}
	
	public void clearCache() {
		this.cachedIndex.clear();
	}
	
	public void addUser(XElement usr) {
		this.cachedIndex.put(usr.getAttribute("Username"), usr);
		
		// make sure we have an encrypted password for use with verify
		if (usr.hasEmptyAttribute("Password") && usr.hasNotEmptyAttribute("PlainPassword"))
			usr.withAttribute("Password", ApplicationHub.getClock().getObfuscator().hashPassword(usr.getAttribute("PlainPassword")));
		
		if (usr.hasEmptyAttribute("FirstName"))
			usr.withAttribute("FirstName", "[unknown]");
		
		if (usr.hasEmptyAttribute("LastName"))
			usr.withAttribute("LastName", "[unknown]");
	}
}