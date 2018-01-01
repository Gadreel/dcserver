/* ************************************************************************
#
#  designCraft.io
#
#  http://designcraft.io/
#
#  Copyright:
#    Copyright 2012 eTimeline, LLC. All rights reserved.
#
#  License:
#    See the license.txt file in the project's top-level directory for details.
#
#  Authors:
#    * Andy White
#
************************************************************************ */
package dcraft.db.request.common;

import dcraft.db.request.update.ConditionalValue;
import dcraft.db.request.update.InsertRecordRequest;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.struct.CompositeStruct;
import dcraft.struct.ListStruct;
import dcraft.util.StringUtil;

/**
 * Insert a new user record into dcDatabase.  Username is required.
 * 
 * @author Andy
 *
 */
public class AddUserRequest extends InsertRecordRequest {
	static public AddUserRequest of(String username) {
		return new AddUserRequest(username);
	}

	protected ConditionalValue username = new ConditionalValue();
	protected ConditionalValue firstname = new ConditionalValue();
	protected ConditionalValue lastname = new ConditionalValue();
	protected ConditionalValue email = new ConditionalValue();
	protected ConditionalValue phone = new ConditionalValue();
	protected ConditionalValue backupemail = new ConditionalValue();
	protected ConditionalValue password = new ConditionalValue();
	protected ConditionalValue locale = new ConditionalValue();
	protected ConditionalValue chrono = new ConditionalValue();
	protected ConditionalValue confirmed = new ConditionalValue();
	protected ConditionalValue confirmcode = new ConditionalValue();
	protected ConditionalValue desc = new ConditionalValue();
	protected ListStruct badges = ListStruct.list();
			
	public AddUserRequest withUsername(String v) {
		this.username.setValue(v);
		return this;
	}
	
	public AddUserRequest withDescription(String v) {
		this.desc.setValue(v);
		return this;
	}
	
	public AddUserRequest withBadges(ListStruct v) {
		this.badges = v;
		return this;
	}
	
	public AddUserRequest addBadges(String... v) {
		for (String name : v)
			this.badges.with(name);

		return this;
	}
	
	public AddUserRequest withFirstName(String v) {
		this.firstname.setValue(v);
		return this;
	}
	
	public AddUserRequest withLastName(String v) {
		this.lastname.setValue(v);
		return this;
	}
	
	public AddUserRequest withPassword(String v) {
		this.password.setValue(v);
		return this;
	}
	
	public AddUserRequest withEmail(String v) {
		this.email.setValue(v);
		return this;
	}
	
	public AddUserRequest withBackupEmail(String v) {
		this.backupemail.setValue(v);
		return this;
	}

	public AddUserRequest withPhone(String v) {
		this.phone.setValue(v);
		return this;
	}

	public AddUserRequest withLocale(String v) {
		this.locale.setValue(v);
		return this;
	}
	
	public AddUserRequest withChronology(String v) {
		this.chrono.setValue(v);
		return this;
	}
	
	public AddUserRequest withConfirmed(boolean v) {
		this.confirmed.setValue(v);
		
		if (v)
			this.confirmcode.clear();
		else
			this.confirmcode.setValue(StringUtil.buildSecurityCode());

		return this;
	}
	
	public AddUserRequest withConfirmCode(String v) {
		this.confirmcode.setValue(v);
		return this;
	}

	/**
	 * @return recovery code for user
	 */
	public String getConfirmCode() {
		return (String)this.confirmcode.getValue();
	}
	
	public AddUserRequest(String username) {
		//this.filter = "dcIsAccountTaken";

		this.withTable("dcUser");
		this.withUsername(username);
		this.withConfirmed(true);
	}
	
	@Override
	public CompositeStruct buildParams() {
		String uname = this.username.isSet() ? ((String) this.username.getValue()).trim().toLowerCase() : null;
		
		if (StringUtil.isEmpty(uname) || "guest".equals(uname)) {
			Logger.errorTr(127);
			return null;
		}
		
		String pword = ((String) this.password.getValue()).trim();
		
		if (! AddUserRequest.meetsPasswordPolicy(pword, false))
			return null;		
		
		this.withSetField("dcUsername", uname);
		this.withSetField("dcFirstName", this.firstname);
		this.withSetField("dcLastName", this.lastname);
		this.withSetField("dcEmail", this.email);
		this.withSetField("dcPhone", this.phone);
		this.withSetField("dcBackupEmail", this.backupemail);
		this.withSetField("dcDescription", this.desc);
		this.withSetField("dcLocale", this.locale);
		this.withSetField("dcChronology", this.chrono);
		this.withSetField("dcConfirmed", this.confirmed);
		this.withSetField("dcConfirmCode", this.confirmcode);
		
		// this works for insert, but Set approach works with both insert and update - see UpdateUserRequest
		this.withSetList("dcBadges", this.badges);
		
		// password crypto 
		if (this.password.isSet())
			try {
				this.withSetField("dcPassword", OperationContext.getOrThrow().getUserContext().getTenant().getObfuscator().hashPassword(pword));
			} 
			catch (OperatingContextException x) {
				Logger.error("Unable to update user password: " + x);
			}
		
		return super.buildParams();	
	}
	
	/**
	 * Checks that a given password meets the applications password policy.  
	 * 
	 * @param password proposed password
	 * @param warnMode produces warnings instead of errors
	 */
	static public boolean meetsPasswordPolicy(String password, boolean warnMode) {
		// TODO make this into a configurable beast
		
		if (StringUtil.isEmpty(password)) {
			if (warnMode)
				Logger.warnTr(125);
			else
				Logger.errorTr(125);
			
			return false;
		}
		
		if (password.length() < 6) {
			if (warnMode)
				Logger.warnTr(126);
			else
				Logger.errorTr(126);
			
			return false;
		}
		
		if (AddUserRequest.isSuspectPassword(password)) {
			if (warnMode)
				Logger.warnTr(135);
			else
				Logger.errorTr(135);
			
			return false;
		}
		
		return true;
	}
	
	/*
	 * trying to track hack logins - create a list of suspect passwords TODO configurable
	 * 
	 * current list from http://www.splashdata.com/press/PR121023.htm which has a yearly list
	 */
	
	static public boolean isSuspectPassword(String pass) {
		pass = pass.trim().toLowerCase();
		
		if ("password".equals(pass) || "123456".equals(pass) || "12345678".equals(pass) || "abc123".equals(pass) ||
				 "qwerty".equals(pass) || "monkey".equals(pass) || "letmein".equals(pass) || "dragon".equals(pass))
			return true;
		
		if ("111111".equals(pass) || "baseball".equals(pass) || "iloveyou".equals(pass) || "trustno1".equals(pass) ||
				 "1234567".equals(pass) || "sunshine".equals(pass) || "master".equals(pass) || "123123".equals(pass))
			return true;
		
		if ("welcome".equals(pass) || "shadow".equals(pass) || "ashley".equals(pass) || "football".equals(pass) ||
				 "jesus".equals(pass) || "michael".equals(pass) || "ninja".equals(pass) || "mustang".equals(pass))
			return true;
		
		if ("password1".equals(pass) || "temp123".equals(pass) || "a1s2d3f4".equals(pass) || "a1s2d3".equals(pass))
			return true;
		
		return false;		
	}
}
