package dcraft.core.db.auth;

import dcraft.cms.thread.db.ThreadUtil;
import dcraft.core.db.UserDataUtil;
import dcraft.db.DatabaseAdapter;
import dcraft.db.DatabaseException;
import dcraft.db.ICallContext;
import dcraft.db.proc.call.SignIn;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.Task;
import dcraft.task.TaskHub;
import dcraft.util.StringUtil;

import java.time.ZonedDateTime;
import java.util.List;

public class Confirm extends SignIn {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		TablesAdapter db = TablesAdapter.ofNow(request);
		DatabaseAdapter conn = request.getInterface();

		// TODO part of Trust monitoring -- boolean suspect =
		//if (AddUserRequest.meetsPasswordPolicy(password, true).hasLogLevel(DebugLevel.Warn))
		//	params.withField("Suspect", true);

		String origin = OperationContext.getOrThrow().getOrigin();

		String id = ThreadUtil.getThreadId(db, data);

		if (StringUtil.isEmpty(id)) {
			Logger.error("Confirm request not found");

			try {
				conn.set("root", "dcIPTrust", origin, ZonedDateTime.now(), 1);
			}
			catch (DatabaseException x) {
				Logger.error("Unable to set IPTrust: " + x);
			}

			callback.returnEmpty();
			return;
		}
		
		boolean proxy = OperationContext.getOrThrow().getUserContext().isTagged("Admin", "Staff");
		
		// TODO check age of the recovery message, limit access to two hours (or configure)

		RecordStruct attrs = Struct.objectToRecord(db.getStaticScalar("dcmThread", id, "dcmSharedAttributes"));

		if (attrs != null) {
			// code must match, or confirm must be by staff/admin user
			if (data.getFieldAsString("Code").equals(attrs.getFieldAsString("Code")) || proxy) {
				String uid = attrs.getFieldAsString("UserId");
				
				// if already confirmed then don't create new user
				if (StringUtil.isEmpty(uid)) {
					DbRecordRequest req = UserDataUtil.addUserWithConditions(attrs, true);
					
					if (req == null) {
						callback.returnEmpty();
						return;
					}
					
					uid = TableUtil.updateRecord(db, req);
					
					attrs.with("UserId", uid);
					
					db.setStaticScalar("dcmThread", id, "dcmSharedAttributes", attrs);
				}
				
				// archive the notice
				ThreadUtil.updateFolder(db, id, "/NoticesPool", "/Archive", false);
				
				/*
				List<String> parties = db.getStaticListKeys("dcmThread", id, "dcmFolder");
				
				for (String party : parties)
					ThreadUtil.updateFolder(db, id, party, "/Archive", true);
					
				 */
				
				// TODO use task queue instead
				TaskHub.submit(Task.ofSubtask("User confirmed code trigger", "USER")
						.withTopic("Batch")
						.withMaxTries(5)
						.withTimeout(10)        // TODO this should be graduated - 10 minutes moving up to 30 minutes if fails too many times
						.withParams(RecordStruct.record()
								.with("Id", id)
						)
						.withScript(CommonPath.from("/dcw/user/event-user-confirmed.dcs.xml")));

				// sign in if not confirmed by admin or staff
				if (! proxy) {
					SignIn.signIn(request, db, uid, true);
					
					callback.returnValue(OperationContext.getOrThrow().getUserContext()
							.deepCopyFields("UserId", "Username", "FirstName", "LastName", "Email",
									"Locale", "Chronology", "Badges"));
				}
				else {
					callback.returnValue(RecordStruct.record().with("UserId", uid));
				}

				return;
			}
			else {
				Logger.error("Confirmation code does not match.");

				try {
					conn.set("root", "dcIPTrust", origin, ZonedDateTime.now(), 1);
				}
				catch (DatabaseException x) {
					Logger.error("Unable to set IPTrust: " + x);
				}
			}
		}
		else {
			Logger.error("Account data missing.");

			try {
				conn.set("root", "dcIPTrust", origin, ZonedDateTime.now(), 1);
			}
			catch (DatabaseException x) {
				Logger.error("Unable to set IPTrust: " + x);
			}
		}

		Logger.errorTr(123);

		callback.returnEmpty();
	}
}
