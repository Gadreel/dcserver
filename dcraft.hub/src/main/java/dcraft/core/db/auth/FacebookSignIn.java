package dcraft.core.db.auth;

import dcraft.db.Constants;
import dcraft.db.DatabaseAdapter;
import dcraft.db.DatabaseException;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.proc.call.SignIn;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.request.update.UpdateRecordRequest;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.db.util.ByteUtil;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.op.UserContext;
import dcraft.interchange.facebook.FacebookUtil;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.tenant.TenantHub;
import dcraft.util.StringUtil;

import java.time.ZonedDateTime;

public class FacebookSignIn extends SignIn {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		RecordStruct userInfo = FacebookUtil.fbSignIn(data.getFieldAsString("Token"));

		if (userInfo == null) {
			callback.returnEmpty();
			return;
		}

		DatabaseAdapter conn = request.getInterface();
		TablesAdapter db = TablesAdapter.ofNow(request);

		// TODO part of Trust monitoring -- boolean suspect =
		//if (AddUserRequest.meetsPasswordPolicy(password, true).hasLogLevel(DebugLevel.Warn))
		//	params.withField("Suspect", true);

		String origin = OperationContext.getOrThrow().getOrigin();

		try {
			int trustcnt = 0;
			byte[] recid = conn.nextPeerKey("root", "dcIPTrust", origin, null);

			while (recid != null) {
				trustcnt++;

				if (trustcnt > 9)
					break;

				recid = conn.nextPeerKey("root", "dcIPTrust", origin, ByteUtil.extractValue(recid));
			}

			if (trustcnt > 9) {
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

		Object userid = db.firstInIndex("dcUser", "dcFacebookId", userInfo.getFieldAsString("id"), false);

		if (userid != null) {
			uid = userid.toString();
		}
		else {
			userid = db.firstInIndex("dcUser", "dcUsername", userInfo.getFieldAsString("email").trim().toLowerCase(), false);

			if (userid != null) {
				uid = userid.toString();

				// link the id
				DbRecordRequest req = UpdateRecordRequest.update()
						.withTable("dcUser")
						.withId(uid)
						.withUpdateField("dcFacebookId", userInfo.getFieldAsString("id"));

				TableUtil.updateRecord(db, req);
			}
		}

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

		boolean confirmed = Struct.objectToBooleanOrFalse(db.getStaticScalar("dcUser", uid, "dcConfirmed"));

		// only confirmed users can login with their password - user's can always login with a validate confirm code
		if (confirmed) {
			this.signIn(request, db, uid);		// callback handled in here
			return;
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
}
