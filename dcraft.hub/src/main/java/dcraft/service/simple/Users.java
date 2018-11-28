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
package dcraft.service.simple;

import dcraft.db.request.DataRequest;
import dcraft.db.request.common.AddUserRequest;
import dcraft.db.request.common.RequestFactory;
import dcraft.db.request.common.UpdateUserRequest;
import dcraft.db.request.common.UsernameLookupRequest;
import dcraft.db.request.query.LoadRecordRequest;
import dcraft.db.request.query.SelectDirectRequest;
import dcraft.db.request.query.SelectFields;
import dcraft.db.request.update.RetireRecordRequest;
import dcraft.db.request.update.ReviveRecordRequest;
import dcraft.filestore.CommonPath;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.locale.LocaleUtil;
import dcraft.service.ServiceHub;
import dcraft.service.ServiceRequest;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.Task;
import dcraft.task.TaskHub;

/*
 * AuthService can not be remote, though it may call a remote service
 * 
 * VERIFY
 * 
 * auth token is key here - if present in request and we find it, then pass (no matter session id)
 * 	if present in request and we don't find it then reset to Guest and return error
 *  if not present then check creds
 *  
 * NOTICE
 * 
 * Simple service does not (yet - TODO) timeout on the auth tokens, will collect forever
 * 
 */

public class Users {
	static public boolean handle(ServiceRequest request, OperationOutcomeStruct callback, CoreDatabase db) throws OperatingContextException {
		if ("LoadSelf".equals(request.getOp()) || "Load".equals(request.getOp())) {
			if (ResourceHub.getResources().getDatabases().hasDefaultDatabase()) {
				// so we trigger stored proc instead
				return false;
				
				/*
				LoadRecordRequest req = LoadRecordRequest.of("dcUser")
						.withId("Load".equals(request.getOp())
								? request.getDataAsRecord().getFieldAsString("Id")
								: OperationContext.getOrThrow().getUserContext().getUserId()
						)
						.withNow()
						.withSelect(SelectFields.select()
								.with("Id")
								.with("dcUsername", "Username")
								.with("dcFirstName", "FirstName")
								.with("dcLastName", "LastName")
								.withForeignField("dcGroup", "Groups", "dcName")
								.with("dcEmail", "Email")
								.with("dcBackupEmail", "BackupEmail")
								.with("dcPhone", "Phone")
								.with("dcLocale", "Locale")
								.with("dcChronology", "Chronology")
								.with("dcDescription", "Description")
								.with("dcConfirmed", "Confirmed")
								.with("dcBadges", "Badges")
						);
				
				ServiceHub.call(req.toServiceRequest().withOutcome(callback));
				*/
			}
			else {
				// TODO code op
				callback.returnEmpty();
			}
			
			return true;
		}
		
		if ("UpdateSelf".equals(request.getOp()) || "Update".equals(request.getOp())) {
			if (ResourceHub.getResources().getDatabases().hasDefaultDatabase()) {
				// so we trigger stored proc instead
				return false;
				
				/*
				RecordStruct rec = request.getDataAsRecord();
				
				UpdateUserRequest req = new UpdateUserRequest("Update".equals(request.getOp())
						? rec.getFieldAsString("Id")
						: OperationContext.getOrThrow().getUserContext().getUserId());
				
				if (rec.hasField("Username"))
					req.setUsername(rec.getFieldAsString("Username"));
				
				if (rec.hasField("FirstName"))
					req.setFirstName(rec.getFieldAsString("FirstName"));
				
				if (rec.hasField("LastName"))
					req.setLastName(rec.getFieldAsString("LastName"));
				
				if (rec.hasField("Email"))
					req.setEmail(rec.getFieldAsString("Email"));
				
				if (rec.hasField("BackupEmail"))
					req.setBackupEmail(rec.getFieldAsString("BackupEmail"));

				if (rec.hasField("Phone"))
					req.withPhone(rec.getFieldAsString("Phone"));

				if (rec.hasField("Locale"))
					req.setLocale(LocaleUtil.normalizeCode(rec.getFieldAsString("Locale")));
				
				if (rec.hasField("Chronology"))
					req.setChronology(rec.getFieldAsString("Chronology"));
				
				if (rec.hasField("Password"))
					req.setPassword(rec.getFieldAsString("Password"));
				
				// not allowed for Self (see schema)
				if (rec.hasField("Confirmed"))
					req.setConfirmed(rec.getFieldAsBoolean("Confirmed"));
				
				// not allowed for Self (see schema)
				if (rec.hasField("Description"))
					req.setDescription(rec.getFieldAsString("Description"));
				
				// not allowed for Self (see schema)
				if (rec.hasField("Badges"))
					req.setBadges(rec.getFieldAsList("Badges"));
				
				ServiceHub.call(req.toServiceRequest().withOutcome(callback));
				*/
			}
			else {
				// TODO code op
				callback.returnEmpty();
			}
			
			return true;
		}
		
		if ("RetireSelf".equals(request.getOp()) || "Retire".equals(request.getOp())) {
			if (ResourceHub.getResources().getDatabases().hasDefaultDatabase()) {
				ServiceHub.call(RetireRecordRequest.of("dcUser", "Retire".equals(request.getOp())
								? request.getDataAsRecord().getFieldAsString("Id") : OperationContext.getOrThrow().getUserContext().getUserId())
						.toServiceRequest()
						.withOutcome(new OperationOutcomeStruct() {
							@Override
							public void callback(Struct result) throws OperatingContextException {
								if ("RetireSelf".equals(request.getOp()))
									ServiceHub.call(ServiceRequest.of("dcCoreServices", "Authentication", "SignOut")
											.withOutcome(callback));
								else
									callback.returnEmpty();
							}
						})
				);
			}
			else {
				// TODO code op
				callback.returnEmpty();
			}
			
			return true;
		}
		
		if ("Add".equals(request.getOp())) {
			if (ResourceHub.getResources().getDatabases().hasDefaultDatabase()) {
				// so we trigger stored proc instead
				return false;
				
				/*
				RecordStruct rec = request.getDataAsRecord();

				AddUserRequest req = new AddUserRequest(rec.getFieldAsString("Username"));

				if (rec.hasField("FirstName"))
					req.withFirstName(rec.getFieldAsString("FirstName"));

				if (rec.hasField("LastName"))
					req.withLastName(rec.getFieldAsString("LastName"));

				if (rec.hasField("Email"))
					req.withEmail(rec.getFieldAsString("Email"));

				if (rec.hasField("BackupEmail"))
					req.withBackupEmail(rec.getFieldAsString("BackupEmail"));

				if (rec.hasField("Phone"))
					req.withPhone(rec.getFieldAsString("Phone"));

				if (rec.hasField("Locale"))
					req.withLocale(LocaleUtil.normalizeCode(rec.getFieldAsString("Locale")));

				if (rec.hasField("Chronology"))
					req.withChronology(rec.getFieldAsString("Chronology"));

				if (rec.hasField("Password"))
					req.withPassword(rec.getFieldAsString("Password"));

				if (rec.hasField("Confirmed"))
					req.withConfirmed(rec.getFieldAsBoolean("Confirmed"));
				else
					req.withConfirmed(true);

				if (rec.hasField("ConfirmCode"))
					req.withConfirmCode(rec.getFieldAsString("ConfirmCode"));

				if (rec.hasField("Description"))
					req.withDescription(rec.getFieldAsString("Description"));

				if (rec.hasField("Badges"))
					req.withBadges(rec.getFieldAsList("Badges"));

				ServiceHub.call(req.toServiceRequest().withOutcome(callback));
				*/
			}
			else {
				// TODO code op
				callback.returnEmpty();
			}

			return true;
		}

		if ("Revive".equals(request.getOp())) {
			if (ResourceHub.getResources().getDatabases().hasDefaultDatabase()) {
				ServiceHub.call(ReviveRecordRequest.of("dcUser", request.getDataAsRecord().getFieldAsString("Id"))
						.toServiceRequest()
						.withOutcome(callback));
			}
			else {
				// TODO code op
				callback.returnEmpty();
			}

			return true;
		}
		
		if ("SetBadges".equals(request.getOp())) {
			if (ResourceHub.getResources().getDatabases().hasDefaultDatabase()) {
				ListStruct users = request.getDataAsRecord().getFieldAsList("Users");
				ListStruct tags = request.getDataAsRecord().getFieldAsList("Badges");

				ServiceHub.call(RequestFactory.makeSet("dcUser", "dcBadges", users, tags)
						.toServiceRequest()
						.withOutcome(callback));
			}
			else {
				// TODO code op
				callback.returnEmpty();
			}

			return true;
		}
		
		if ("AddBadges".equals(request.getOp())) {
			if (ResourceHub.getResources().getDatabases().hasDefaultDatabase()) {
				ListStruct users = request.getDataAsRecord().getFieldAsList("Users");
				ListStruct tags = request.getDataAsRecord().getFieldAsList("Badges");

				ServiceHub.call(RequestFactory.addToSet("dcUser", "dcBadges", users, tags)
						.toServiceRequest()
						.withOutcome(callback));
			}
			else {
				// TODO code op
				callback.returnEmpty();
			}

			return true;
		}
		
		if ("RemoveBadges".equals(request.getOp())) {
			if (ResourceHub.getResources().getDatabases().hasDefaultDatabase()) {
				ListStruct users = request.getDataAsRecord().getFieldAsList("Users");
				ListStruct tags = request.getDataAsRecord().getFieldAsList("Badges");

				ServiceHub.call(RequestFactory.removeFromSet("dcUser", "dcBadges", users, tags)
						.toServiceRequest()
						.withOutcome(callback));
			}
			else {
				// TODO code op
				callback.returnEmpty();
			}

			return true;
		}
		
		if ("UsernameLookup".equals(request.getOp())) {
			if (ResourceHub.getResources().getDatabases().hasDefaultDatabase()) {
				ServiceHub.call(UsernameLookupRequest.of(request.getDataAsRecord().getFieldAsString("Username"))
						.toServiceRequest()
						.withOutcome(callback));
			}
			else {
				// TODO code op
				callback.returnEmpty();
			}

			return true;
		}
		
		/* deprecated
		if ("ListUsers".equals(request.getOp())) {
			if (ResourceHub.getResources().getDatabases().hasDefaultDatabase()) {
				ServiceHub.call(
						SelectDirectRequest.of("dcUser")
								.withSelect(SelectFields.select()
										.with("Id")
										.with("dcUsername", "Username")
										.with("dcFirstName", "FirstName")
										.with("dcLastName", "LastName")
										.with("dcEmail", "Email")
								)
						.toServiceRequest()
						.withOutcome(callback));
			}
			else {
				// TODO code op
				callback.returnEmpty();
			}

			return true;
		}
		*/

		if ("Search".equals(request.getOp())) {
			// TODO support Term and Badges
			if (ResourceHub.getResources().getDatabases().hasDefaultDatabase()) {
				// so we trigger stored proc instead
				return false;
				
				/*
				ServiceHub.call(
						SelectDirectRequest.of("dcUser")
								.withSelect(SelectFields.select()
										.with("Id")
										.with("dcUsername", "Username")
										.with("dcFirstName", "FirstName")
										.with("dcLastName", "LastName")
										.with("dcEmail", "Email")
								)
								.toServiceRequest()
								.withOutcome(callback));
								*/
			}
			else {
				// TODO code op
				callback.returnEmpty();
			}

			return true;
		}

		if ("InitiateConfirm".equals(request.getOp())) {
			/* TODO review and use UserDataUtil with this

			RecordStruct data = request.getDataAsRecord();
			
			String uname = data.getFieldAsString("Username");
			
			DataRequest req = RequestFactory.initiateRecoveryRequest(uname);
			
			ServiceHub.call(req.toServiceRequest().withOutcome(new OperationOutcomeStruct() {
			   @Override
			   public void callback(Struct result) throws OperatingContextException {
					if (this.hasErrors()) {
						callback.returnEmpty();
						return;
					}
					
					RecordStruct resrec = (RecordStruct) result;
					
					System.out.println("confirm code: " + resrec.getFieldAsString("Code")
						   + " to email: " + resrec.getFieldAsString("Email"));
					
					TaskHub.submit(Task.ofSubtask("User confirm code trigger", "USER")
						   .withTopic("Batch")
						   .withMaxTries(5)
						   .withTimeout(10)		// TODO this should be graduated - 10 minutes moving up to 30 minutes if fails too many times
						   .withParams(RecordStruct.record()
								   .with("Id", resrec.getFieldAsString("Id"))
						   )
						   .withScript(CommonPath.from("/dcw/user/event-user-confirm.dcs.xml")));
							
				   callback.returnEmpty();
			   }
			}));
			*/

			callback.returnEmpty();
			return true;
		}
		
		return false;
	}
}
