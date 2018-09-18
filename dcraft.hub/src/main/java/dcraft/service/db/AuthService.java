package dcraft.service.db;

import dcraft.service.BaseService;
import dcraft.service.IService;
import dcraft.service.ServiceRequest;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.xml.XElement;

public class AuthService extends BaseService {
	@Override
	public boolean handle(ServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException {
		return false;

		/* TODO rework
		OperationContext tc = OperationContext.getOrThrow();
		UserContext uc = tc.getUserContext();

		// uc is different from sess.getUser as uc may have credentials with it...sess should not
		Session sess = tc.getSession();
		
		// TODO we are a specialized service - should be allowed direct access to DB, maybe?
		
		IDatabaseManager db = DatabaseHub.defaultDb();
		
		if (db == null) {
			Logger.errorTr(303);
			callback.returnResult();
			return false;
		}
		
		//System.out.println("Auth: " + feature + " - " + op);
		
		if ("Facebook".equals(feature)) {
			if ("LinkAccount".equals(op)) {
				// try to authenticate
				RecordStruct creds = msg.getFieldAsRecord("Body");
				
				String fbtoken = creds.getFieldAsString("AccessToken");
				
				RecordStruct fbinfo = AuthService.fbSignIn(fbtoken, null);		// TODO use FB secret key someday? for app proof...
				
				if (callback.hasErrors() || (fbinfo == null)) {
					Logger.error("Missing Facebook fields");
					callback.returnResult();
					return true;
				}
				
				// TODO allow only `verified` fb users?
				if (fbinfo.isFieldEmpty("id") || fbinfo.isFieldEmpty("email")
						 || fbinfo.isFieldEmpty("first_name") || fbinfo.isFieldEmpty("last_name")) {		
					Logger.error("Missing Facebook fields");
					callback.returnResult();
					return true;
				}
				
				String uid = fbinfo.getFieldAsString("id");
									
				UpdateRecordRequest req = new UpdateRecordRequest();
				
				req
					.withTable("dcUser")
					.withId(OperationContext.getOrThrow().getUserContext().getUserId())
					.withUpdateField("dcmFacebookId", uid);
				
				db.submit(req, new StructOutomeFinal(callback) );
				
				return true;
			}
		}
		else if ("Authentication".equals(feature)) {
			if ("SignIn".equals(op)) {
				
				if (sess == null) {
					OperationContext.switchUser(request.getContext(), UserContext.allocateGuest());
					
					Logger.errorTr(442);
					Logger.error("Session not found");
					callback.returnResult();
					return true;
				}
				
				LoadRecordRequest req = new LoadRecordRequest()
					.withTable("dcUser")
					.withId(uc.getUserId())
					.withNow()
					.withSelect(new SelectFields()
						.withField("dcUsername", "Username")
						.withField("dcFirstName", "FirstName")
						.withField("dcLastName", "LastName")
						.withField("dcEmail", "Email")
					);				
				
				db.submit(req, new StructOutcome() {
					@Override
					public void callback(CompositeStruct result) {
						if (this.hasErrors() || (result == null)) {
							tc.getTenant().authEvent(op, "Fail", uc);
							AuthService.this.clearUserContext(sess, request.getContext());
							Logger.errorTr(442);
						}
						else {
							tc.getTenant().authEvent(op, "Success", uc);
						}
						
						callback.returnValue(result);
					}
				});						
				
				return true;
			}			

			
			if ("SignInFacebook".equals(op)) {
				
				if (sess == null) {
					OperationContext.switchUser(request.getContext(), UserContext.allocateGuest());
					
					Logger.errorTr(442);
					Logger.error("Session not found");
					callback.returnResult();
					return true;
				}
				
				// TODO check domain settings that FB sign in is allowed
				
				// try to authenticate
				RecordStruct creds = msg.getFieldAsRecord("Body");
				
				//String uid = creds.getFieldAsString("UserId");
				String fbtoken = creds.getFieldAsString("AccessToken");
				
				RecordStruct fbinfo = AuthService.fbSignIn(fbtoken, null);		// TODO use FB secret key someday? for app proof...
				
				if (callback.hasErrors() || (fbinfo == null)) {
					tc.getTenant().authEvent("SignIn", "Fail", uc);
					AuthService.this.clearUserContext(sess, OperationContext.get());
					Logger.errorTr(442);
					callback.returnResult();
					return true;
				}
				
				// TODO allow only `verified` fb users?
				if (fbinfo.isFieldEmpty("id") || fbinfo.isFieldEmpty("email")
						 || fbinfo.isFieldEmpty("first_name") || fbinfo.isFieldEmpty("last_name")) {		
					tc.getTenant().authEvent("SignIn", "Fail", uc);
					AuthService.this.clearUserContext(sess, OperationContext.get());
					Logger.errorTr(442);
					callback.returnResult();
					return true;
				}
				
				String uid = fbinfo.getFieldAsString("id");
				
				// sigin callback
				Consumer<String> signincb = new Consumer<String>() {					
					@Override
					public void accept(String userid) {
						DataRequest tp1 = RequestFactory.startSessionRequest(userid);
						
						// TODO for all services, be sure we return all messages from the submit with the message
						db.submit(tp1, new StructOutcome() {
							@Override
							public void callback(CompositeStruct result) {
								RecordStruct sirec = (RecordStruct) result;
								OperationContext ctx = request.getContext();
								
								//System.out.println("auth 2: " + request.getContext().isElevated());
								
								if (this.hasErrors() || (sirec == null)) {
									tc.getTenant().authEvent("SignIn", "Fail", uc);
									AuthService.this.clearUserContext(sess, ctx);
									Logger.errorTr(442);
									callback.returnResult();
									return;
								}

								ListStruct atags = sirec.getFieldAsList("AuthorizationTags");
								atags.withItem("User");
								
								// TODO make locale smart
								String fullname = "";
								
								if (!sirec.isFieldEmpty("FirstName"))
									fullname = sirec.getFieldAsString("FirstName");
								
								if (!sirec.isFieldEmpty("LastName") && StringUtil.isNotEmpty(fullname))
									fullname += " " + sirec.getFieldAsString("LastName");
								else if (!sirec.isFieldEmpty("LastName"))
									fullname = sirec.getFieldAsString("LastName");
								
								if (StringUtil.isEmpty(fullname))
									fullname = "[unknown]";
								
								UserContext usr = sess.getUser().toBuilder() 
										.withVerified(true)
										.withAuthToken(sirec.getFieldAsString("AuthToken"))
										.withUserId(sirec.getFieldAsString("UserId"))
										.withUsername(sirec.getFieldAsString("Username"))
										.withFullName(fullname)		
										.withEmail(sirec.getFieldAsString("Email"))
										.withBadges(atags)
										.toUserContext();

								sess.withUser(usr);
								
								OperationContext.switchUser(ctx, usr);
								
								tc.getTenant().authEvent("SignIn", "Success", usr);
								
								callback.returnValue(RecordStruct.record()
									.with("Username", sirec.getFieldAsString("Username"))
									.with("FirstName", sirec.getFieldAsString("FirstName"))
									.with("LastName", sirec.getFieldAsString("LastName"))
									.with("Email", sirec.getFieldAsString("Email"))
								);
							}
						});
					}
				};
				
				// -----------------------------------------
				// find user - update or insert user record
				// -----------------------------------------
				
				db.submit(
						new SelectDirectRequest()
							.withTable("dcUser")
							.withSelect(new SelectFields()
									.withField("Id")
									.withField("dcUsername", "Username")
									.withField("dcFirstName", "FirstName")
									.withField("dcLastName", "LastName")
									.withField("dcEmail", "Email")
							)
							.withWhere(
									new WhereEqual(new WhereField("dcmFacebookId"), uid)		// TODO or where `username` = `fb email` - maybe?
							),
						new StructOutcome() {
							@Override
							public void callback(CompositeStruct uLookupResult) {
								if (this.hasErrors() || (uLookupResult == null)) {
									tc.getTenant().authEvent("SignIn", "Fail", uc);
									Logger.error("Error finding user record");
									callback.returnResult();
									return;
								}
								
								ListStruct ulLookupResult = (ListStruct) uLookupResult;
								
								if (ulLookupResult.size() == 0) {
									// insert new user record
									InsertRecordRequest req = new InsertRecordRequest();
									
									req
										.withTable("dcUser")		
										.withSetField("dcUsername", fbinfo.getFieldAsString("email"))
										.withSetField("dcEmail", fbinfo.getFieldAsString("email"))
										.withSetField("dcFirstName", fbinfo.getFieldAsString("first_name"))
										.withSetField("dcLastName", fbinfo.getFieldAsString("last_name"))
										.withSetField("dcmFacebookId", uid)
										.withSetField("dcConfirmed", true);									
									
									// TODO look at fb `locale` and `timezone` too
									
									db.submit(req, new StructOutcome() {										
										@Override
										public void callback(CompositeStruct result) {
											if (this.hasErrors()) {
												tc.getTenant().authEvent("SignIn", "Fail", uc);
												callback.returnResult();
											}
											else
												signincb.accept(((RecordStruct)result).getFieldAsString("Id"));
										}
									});
								}
								else {
									String dcuid = ulLookupResult.getItemAsRecord(0).getFieldAsString("Id");
									
									UpdateRecordRequest req = new UpdateRecordRequest();
									
									req
										.withTable("dcUser")
										.withId(dcuid)
										// TODO add these once UpdateField works with Dynamic Scalar
										//.withUpdateField("dcUsername", fbinfo.getFieldAsString("email"))
										//.withUpdateField("dcEmail", fbinfo.getFieldAsString("email"))
										//.withUpdateField("dcFirstName", fbinfo.getFieldAsString("first_name"))
										//.withUpdateField("dcLastName", fbinfo.getFieldAsString("last_name"))
										.withUpdateField("dcmFacebookId", uid)
										.withUpdateField("dcConfirmed", true);									
									
									// TODO look at fb `locale` and `timezone` too
									
									db.submit(req, new StructOutcome() {										
										@Override
										public void callback(CompositeStruct result) {
											if (this.hasErrors()) {
												tc.getTenant().authEvent("SignIn", "Fail", uc);
												request.returnResult();
											}
											else
												signincb.accept(dcuid);
										}
									});
								}
							}
						}
				);
				
				return true;
			}			
			
			// TODO now that we trust the token in Session this won't get called often - think about how to keep
			// auth token fresh in database - especially since the token will expire in 30 minutes
			if ("Verify".equals(op)) {
				
				if (sess == null) {
					OperationContext.switchUser(request.getContext(), UserContext.allocateGuest());
					
					Logger.errorTr(442);
					Logger.error("Session not found");
					callback.returnResult();
					return true;
				}
				
				// if token is present that is all we use, get rid of token if you want a creds check
				
				String authToken = uc.getAuthToken();
				
				if (StringUtil.isNotEmpty(authToken)) {
					DataRequest tp1 = RequestFactory.verifySessionRequest(uc.getAuthToken());
					
					db.submit(tp1, new StructOutcome() {
						@Override
						public void callback(CompositeStruct result) {
							RecordStruct urec = (RecordStruct) result;
							OperationContext ctx = request.getContext();
							
							if (this.hasErrors() || (urec == null)) {
								tc.getTenant().authEvent(op, "Fail", uc);
								AuthService.this.clearUserContext(sess, ctx);
								Logger.errorTr(442);
							}
							else {
								//System.out.println("verify existing");
								ListStruct atags = urec.getFieldAsList("AuthorizationTags");
								atags.withItem("User");								
								
								// TODO make locale smart
								String fullname = "";
								
								if (!urec.isFieldEmpty("FirstName"))
									fullname = urec.getFieldAsString("FirstName");
								
								if (!urec.isFieldEmpty("LastName") && StringUtil.isNotEmpty(fullname))
									fullname += " " + urec.getFieldAsString("LastName");
								else if (!urec.isFieldEmpty("LastName"))
									fullname = urec.getFieldAsString("LastName");
								
								if (StringUtil.isEmpty(fullname))
									fullname = "[unknown]";

								UserContext usr = sess.getUser().toBuilder() 
										.withVerified(true)
										.withUserId(urec.getFieldAsString("UserId"))
										.withUsername(urec.getFieldAsString("Username"))
										.withFullName(fullname)	
										.withEmail(urec.getFieldAsString("Email"))
										.withBadges(atags)
										.toUserContext();
								
								sess.withUser(usr);
								
								OperationContext.switchUser(ctx, usr);
								
								tc.getTenant().authEvent(op, "Success", usr);
							}
							
							callback.returnResult();
						}
					});
					
					return true;
				}				
				
				// else try to authenticate
				RecordStruct creds = uc.getCredentials();  // msg.getFieldAsRecord("Credentials");
				
				if (creds == null) {
					tc.getTenant().authEvent(op, "Fail", uc);
					Logger.errorTr(442);
					callback.returnResult();
					return true;
				}
				
				//System.out.println("auth 1: " + request.getContext().isElevated());
				
				DataRequest tp1 = RequestFactory.signInRequest(creds.getFieldAsString("Username"), 
						creds.getFieldAsString("Password"), creds.getFieldAsString("ClientKeyPrint"));
				
				// TODO for all services, be sure we return all messages from the submit with the message
				db.submit(tp1, new StructOutcome() {
					@Override
					public void callback(CompositeStruct result) {
						RecordStruct sirec = (RecordStruct) result;
						OperationContext ctx = request.getContext();
						
						//System.out.println("auth 2: " + request.getContext().isElevated());
						
						if (this.hasErrors() || (sirec == null)) {
							tc.getTenant().authEvent(op, "Fail", uc);
							AuthService.this.clearUserContext(sess, ctx);
							Logger.errorTr(442);
						}
						else {
							//System.out.println("verify new");
							ListStruct atags = sirec.getFieldAsList("AuthorizationTags");
							atags.withItem("User");
							
							// TODO make locale smart
							String fullname = "";
							
							if (!sirec.isFieldEmpty("FirstName"))
								fullname = sirec.getFieldAsString("FirstName");
							
							if (!sirec.isFieldEmpty("LastName") && StringUtil.isNotEmpty(fullname))
								fullname += " " + sirec.getFieldAsString("LastName");
							else if (!sirec.isFieldEmpty("LastName"))
								fullname = sirec.getFieldAsString("LastName");
							
							if (StringUtil.isEmpty(fullname))
								fullname = "[unknown]";

							UserContext usr = sess.getUser().toBuilder() 
								.withVerified(true)
								.withAuthToken(sirec.getFieldAsString("AuthToken"))
								.withUserId(sirec.getFieldAsString("UserId"))
								.withUsername(sirec.getFieldAsString("Username"))
								.withFullName(fullname)		
								.withEmail(sirec.getFieldAsString("Email"))
								.withBadges(atags)
								.toUserContext();
							
							sess.withUser(usr);
							
							OperationContext.switchUser(ctx, usr);
							
							tc.getTenant().authEvent(op, "Success", usr);
						}
						
						callback.returnResult();
					}
				});
				
				return true;
			}			
			
			if ("SignOut".equals(op)) {
				db.submit(RequestFactory.signOutRequest(uc.getAuthToken()), new StructOutcome() {
					@Override
					public void callback(CompositeStruct result) {
						if (sess != null)
							AuthService.this.clearUserContext(sess, request.getContext());
						
						callback.returnResult();
					}
				});
				
				return true;
			}		
		}
		else if ("Recovery".equals(feature)) {
			if ("InitiateSelf".equals(op) || "InitiateAdmin".equals(op)) {
				String user = msg.bodyRecord().getFieldAsString("User");  
				
				DataRequest req = RequestFactory.initiateRecoveryRequest(user);
				
				db.submit(req, new StructOutcome() {
					@Override
					public void callback(CompositeStruct result) {
						if (this.hasErrors()) { 
							Logger.errorTr(442);
						}
						else {
							String code = ((RecordStruct)result).getFieldAsString("Code");
							String email = ((RecordStruct)result).getFieldAsString("Email");
							String email2 = ((RecordStruct)result).getFieldAsString("BackupEmail");
							
							// TODO send email
							
							System.out.println("Sending recovery code: " + code + " to " + email + " and " + email2);
						}
						
						if ("InitiateAdmin".equals(op))
							// return the code/emails to the admin
							callback.returnValue(result);
						else
							// don't return to guest
							callback.returnResult();
					}
				});
				
				return true;
			}			
		}
		
		return false;
		*/
	}
	
	/*
	public void clearUserContext(Session sess, OperationContext ctx) {
		sess.clearToGuest();
		OperationContext.switchUser(ctx, sess.getUser());
	}
	*/
}
