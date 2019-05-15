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
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import z.dga.db.Util;

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

		// TODO check age of the recovery message, limit access to two hours (or configure)
		
		List<String> parties = db.getStaticListKeys("dcmThread", id, "dcmFolder");
		
		for (String party : parties)
			ThreadUtil.updateFolder(db, id, party, "/Archive", true);

		RecordStruct attrs = Struct.objectToRecord(db.getStaticScalar("dcmThread", id, "dcmSharedAttributes"));

		if (attrs != null) {
			if (data.getFieldAsString("Code").equals(attrs.getFieldAsString("Code")) || OperationContext.getOrThrow().getUserContext().isTagged("Admin")) {
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

				// sign in if not confirmed by admin
				SignIn.signIn(request, db, uid, ! OperationContext.getOrThrow().getUserContext().isTagged("Admin"));

				if (! OperationContext.getOrThrow().getUserContext().isTagged("Admin"))
					callback.returnValue(OperationContext.getOrThrow().getUserContext()
						.deepCopyFields("UserId", "Username", "FirstName", "LastName", "Email",
							"Locale", "Chronology", "Badges"));
				else
					callback.returnValue(RecordStruct.record().with("UserId", uid));

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
			Logger.error("Account fields missing.");

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
