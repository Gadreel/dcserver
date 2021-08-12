package dcraft.core.db.auth;

import dcraft.db.ICallContext;
import dcraft.db.proc.IUpdatingStoredProc;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.request.update.UpdateRecordRequest;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.interchange.facebook.FacebookUtil;
import dcraft.struct.RecordStruct;

public class LinkFacebookAccount implements IUpdatingStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		TablesAdapter db = TablesAdapter.of(request);

		RecordStruct userInfo = FacebookUtil.fbSignIn(data.getFieldAsString("Token"));

		if (userInfo != null) {
			DbRecordRequest req = UpdateRecordRequest.update()
					.withTable("dcUser")
					.withId(OperationContext.getOrThrow().getUserContext().getUserId())
					.withUpdateField("dcFacebookId", userInfo.getFieldAsString("id"));

			TableUtil.updateRecord(db, req);
		}

		callback.returnEmpty();
	}
}
