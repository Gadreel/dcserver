package dcraft.db.request.common;

import dcraft.db.request.DataRequest;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.util.StringUtil;

import java.time.ZonedDateTime;

public class RequestFactory {
	/**
	 * Send an empty request to the database that merely returns the string "PONG".
	 * This request is helpful for verifying that the database is connected and 
	 * responding.
	 * 
	 * @author Andy
	 *
	 */
	static public DataRequest ping() {
		return DataRequest.of("dcPing");
	}
	
	static public DataRequest echo(String text) {
		return DataRequest.of("dcEcho")
				.withParam("Text", text);
	}
	
	/**
	 * Sign in to confirm user, includes user name, password and optional confirm code.
	 * 
	 * @param username entered by user
	 * @param password entered by user
	 */
	static public DataRequest signInRequest(String username, String password, String keyprint) {
		DataRequest request = DataRequest.of("dcSignIn")
			.withParam("Username", (username != null) ? username.trim().toLowerCase() : null);
		
		if (StringUtil.isNotEmpty(password)) 
			request.withParam("Password", password.trim());		// password crypto handled in stored proc
		
		if (StringUtil.isNotEmpty(keyprint))
			request.withParam("ClientKeyPrint", keyprint.trim());
		
		return request;
	}
	
	static public DataRequest signOutRequest() {
		return DataRequest.of("dcSignOut");
	}
	
	/**
	 */
	static public DataRequest verifySessionRequest() {
		return DataRequest.of("dcVerifySession");
	}
	
	/**
	 * Start session for user via user name or user id.
	 * 
	 * @param userid of user
	 */
	static public DataRequest startSessionRequest(String userid) {
		return DataRequest.of("dcStartSession")
				.withParam("UserId", userid);
	}
	
	static public DataRequest startSessionRequestFromName(String username) {
		return DataRequest.of("dcStartSession")
			.withParam("Username", (username != null) ? username.trim().toLowerCase() : null);
	}
	
	static public DataRequest cleanDatabaseRequest(ZonedDateTime threshold) {
		return DataRequest.of("dcCleanup")
				.withParam("LongExpireThreshold", threshold);
	}
	
	/**
	 * Initiate sign on recovery.
	 * 
	 * @param username identifying info entered by user (username or email)
	 */
	static public DataRequest initiateRecoveryRequest(String username) {
		return DataRequest.of("dcInitiateConfirm")
				.withParam("Username", username.toLowerCase());
	}
	
	static public DataRequest removeFromSet(String table, String field, String id, ListStruct values) {
		return removeFromSet(table, field, ListStruct.list(id), values);
	}
	
	static public DataRequest removeFromSet(String table, String field, ListStruct recs, ListStruct values) {
		return DataRequest.of("dcUpdateSet")
			.withParam("Operation", "RemoveFromSet")
			.withParam("Table", table)
			.withParam("Records", recs)
			.withParam("Field", field)
			.withParam("Values", values);
	}
	
	static public DataRequest addToSet(String table, String field, String id, ListStruct values) {
		return addToSet(table, field, ListStruct.list(id), values);
	}
	
	static public DataRequest addToSet(String table, String field, ListStruct recs, ListStruct values) {
		return DataRequest.of("dcUpdateSet")
			.withParam("Operation", "AddToSet")
			.withParam("Table", table)
			.withParam("Records", recs)
			.withParam("Field", field)
			.withParam("Values", values);
	}
	
	static public DataRequest makeSet(String table, String field, String id, ListStruct values) {
		return makeSet(table, field, ListStruct.list(id), values);
	}
	
	static public DataRequest makeSet(String table, String field, ListStruct recs, ListStruct values) {
		return DataRequest.of("dcUpdateSet")
			.withParam("Operation", "MakeSet")
			.withParam("Table", table)
			.withParam("Records", recs)
			.withParam("Field", field)
			.withParam("Values", values);
	}
	
	static public DataRequest addTenantRequest(String tenantAlias) {
		return DataRequest.of("dcAddTenant")
				.withForTenant("root")
				.withParam("Alias", tenantAlias);
	}
	
}
