package dcraft.core.db.auth;

import dcraft.cms.thread.db.ThreadUtil;
import dcraft.db.DatabaseAdapter;
import dcraft.db.DatabaseException;
import dcraft.db.ICallContext;
import dcraft.db.proc.call.SignIn;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.request.update.UpdateRecordRequest;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.db.util.ByteUtil;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.interchange.facebook.FacebookUtil;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;
import z.gei.db.estimator.DataUtil;

import java.time.ZonedDateTime;

public class Recover extends SignIn {
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
			Logger.error("Recovery request not found");

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

		ThreadUtil.updateFolder(db, id, data.getFieldAsString("Party"), "/Archive", true);

		RecordStruct attrs = Struct.objectToRecord(db.getStaticScalar("dcmThread", id, "dcmSharedAttributes"));

		if (attrs != null) {
			if (data.getFieldAsString("Code").equals(attrs.getFieldAsString("Code"))) {
				String uid = attrs.getFieldAsString("UserId");

				// sign in
				SignIn.signIn(request, db, uid, true);

				callback.returnValue(OperationContext.getOrThrow()
						.deepCopyFields("UserId", "Username", "FirstName", "LastName", "Email",
							"Locale", "Chronology", "Badges"));

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
