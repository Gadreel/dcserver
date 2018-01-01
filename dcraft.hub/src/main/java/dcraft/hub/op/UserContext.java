/* ************************************************************************
#
#  designCraft.io
#
#  http://designcraft.io/
#
#  Copyright:
#    Copyright 2014 eTimeline, LLC. All rights reserved.
#
#  License:
#    See the license.txt file in the project's top-level directory for details.
#
#  Authors:
#    * Andy White
#
************************************************************************ */
package dcraft.hub.op;

import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.tenant.Site;
import dcraft.tenant.Tenant;
import dcraft.tenant.TenantHub;

/**
 * User Context works in conjunction with Operation Context and Session to track information about 
 * an authenticated user or guest user.
 * 
 * @author Andy
 */
public class UserContext extends RecordStruct {
	static public UserContext rootUser() {
		return UserContext.rootUser("root", "root");
	}
	
	static public UserContext rootUser(String tenant, String site) {
		UserContext ctx = new UserContext();
		
		ctx.clearToRoot(tenant, site);

		return ctx;
	}
	
	static public UserContext guestUser() {
		return UserContext.guestUser("root", "root");
	}
	
	static public UserContext guestUser(String tenant, String site) {
		UserContext ctx = new UserContext();

		ctx.clearToGuest(tenant, site);

		return ctx;
	}

	static public UserContext user(RecordStruct user) {
		if (user == null)
			user = new RecordStruct();
		
		if (user.isFieldEmpty("UserId") || user.isFieldEmpty("Username") || user.isFieldEmpty("Tenant") || user.isFieldEmpty("Site"))
			return null;
		
		UserContext ctx = new UserContext();
		
		ctx.copyFields(user);

		return ctx;
	}

	// instance code
	
	//protected RecordStruct context = null;

	/**
	 * @return the id of the user, if guest then "00000_000000000000002", if root then "00000_000000000000001"
	 */
	public String getUserId() {
		return this.getFieldAsString("UserId");
	}
	
	public void setUserId(String v) {
		this.with("UserId", v);
	}
	
	public UserContext withUserId(String v) {
		this.setUserId(v);
		return this;
	}

	/**
	 * @return the username of the user
	 */
	public String getUsername() {
		return this.getFieldAsString("Username");
	}
	
	public void setUsername(String v) {
		this.with("Username", v);
	}
	
	public UserContext withUsername(String v) {
		this.setUsername(v);
		return this;
	}

	/**
	 * @return the firstname of the user
	 */
	public String getFirstName() {
		return this.getFieldAsString("FirstName");
	}
	
	public void setFirstName(String v) {
		this.with("FirstName", v);
	}
	
	public UserContext withFirstName(String v) {
		this.setFirstName(v);
		return this;
	}

	/**
	 * @return the laststname of the user
	 */
	public String getLastName() {
		return this.getFieldAsString("LastName");
	}

	public void setLastName(String v) {
		this.with("LastName", v);
	}

	public UserContext withLastName(String v) {
		this.setLastName(v);
		return this;
	}

	/**
	 * @return the primary email of the user
	 */
	public String getEmail() {
		return this.getFieldAsString("Email");
	}
	
	public void setEmail(String v) {
		this.with("Email", v);
	}
	
	public UserContext withEmail(String v) {
		this.setEmail(v);
		return this;
	}

	/**
	 * @return the authentication token
	 */
	public String getAuthToken() {
		return this.getFieldAsString("AuthToken");
	}
	
	public void setAuthToken(String v) {
		this.with("AuthToken", v);
	}
	
	public UserContext withAuthToken(String v) {
		this.setAuthToken(v);
		return this;
	}

	/**
	 * @return chronology to use with this task (for output)
	 */
	public ListStruct getChronology() {
		return this.getFieldAsList("Chronology");
	}
	
	public void setChronology(ListStruct v) {
		this.with("Chronology", v);
	}
	
	public UserContext withChronology(ListStruct v) {
		this.setChronology(v);
		return this;
	}

	/**
	 * @return locale to use with this task for output
	 */
	public ListStruct getLocale() {
		return this.getFieldAsList("Locale");
	}
	
	public void setLocale(ListStruct v) {
		this.with("Locale", v);
	}
	
	public UserContext withLocale(ListStruct v) {
		this.setLocale(v);
		return this;
	}

	public String getTenantAlias() {
		return this.getFieldAsString("Tenant");
	}
	
	public void setTenantAlias(String v) {
		this.with("Tenant", v);
	}
	
	public UserContext withTenantAlias(String v) {
		this.setTenantAlias(v);
		return this;
	}

	public Tenant getTenant() {
		return TenantHub.resolveTenant(this.getTenantAlias());
	}

	public String getSiteAlias() {
		return this.getFieldAsString("Site");
	}
	
	public void setSiteAlias(String v) {
		this.with("Site", v);
	}
	
	public UserContext withSiteAlias(String v) {
		this.setSiteAlias(v);
		return this;
	}

	public Site getSite() {
		Tenant ten = TenantHub.resolveTenant(this.getTenantAlias());
		
		if (ten != null)
			return ten.resolveSite(this.getSiteAlias());
		
		return null;
	}

	public UserContext withBadges(String... v) {
		this.with("Badges", ListStruct.list((Object[])v));
		return this;
	}

	public UserContext withBadges(ListStruct v) {
		this.with("Badges", v);
		return this;
	}

	public UserContext addBadges(String... v) {
		ListStruct tlist = this.getFieldAsList("Badges");
		
		if (tlist == null) 
			this.with("Badges", ListStruct.list((Object[])v));
		else
			tlist.withItem((Object[])v);
		
		return this;
	}
	
	protected UserContext() {
	}

	public boolean looksLikeGuest() {
		return "guest".equals(this.getUsername());
	}
	
	public boolean looksLikeRoot() {
		return "root".equals(this.getUsername());
	}
	
	/**
	 * @param tags to search for with this user
	 * @return true if this user has one of the requested authorization tags  (does not check authentication)
	 */
	public boolean isTagged(String... tags) {
		ListStruct creds = this.getFieldAsList("Badges");
		
		if (creds == null) 
			return false;
		
		for (int i = 0; i < creds.size(); i++) {
			String has = creds.getItemAsString(i);

			for (String wants : tags) {
				if (has.equals(wants))
					return true;
			}
		}
		
		return false;
	}
	
	public void clearToGuest() {
		this.clearToGuest("root", "root");
	}

	public void clearToGuest(String tenant, String site) {
		this
				.withTenantAlias(tenant)
				.withSiteAlias(site)
				.withUserId("00000_000000000000002")
				.withUsername("guest")
				.withFirstName("Guest")
				.withLastName("User")
				.withLocale(null)
				.withChronology(null)
				.withAuthToken(null)
				.withEmail(null)
				.withBadges("Guest");
	}
	
	public void clearToGuestKeepSite() {
		this
				.withUserId("00000_000000000000002")
				.withUsername("guest")
				.withFirstName("Guest")
				.withLastName("User")
				.withLocale(null)
				.withChronology(null)
				.withAuthToken(null)
				.withEmail(null)
				.withBadges("Guest");
	}

	public void clearToRoot() {
		this.clearToRoot("root", "root");
	}

	public void clearToRoot(String tenant, String site) {
		this
				.withTenantAlias(tenant)
				.withSiteAlias(site)
				.withUserId("00000_000000000000001")
				.withUsername("root")
				.withFirstName("Root")
				.withLastName("User")
				.withLocale(null)
				.withChronology(null)
				.withAuthToken(null)
				.withEmail(null)
				.withBadges("User", "Developer", "Admin", "SysAdmin");
	}

	@Override
	public String toString() {
		return this.toPrettyString();		// TODO consider if we should exclude fields
	}

	@Override
	public UserContext deepCopy() {
		UserContext cp = new UserContext();
		this.doCopy(cp);
		return cp;
	}

	/*
	public RecordStruct freezeToRecord() {
		return (RecordStruct) this.context.deepCopy();
	}
	
	public RecordStruct freezeToSafeRecord() {
		return (RecordStruct) this.context.deepCopyExclude("AuthToken");
	}
	*/
}
