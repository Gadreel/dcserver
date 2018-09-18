package dcraft.core.db.user;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.request.query.SelectFields;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.RecordStruct;

public class Load implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		String id = data.getFieldAsString("Id");

		TablesAdapter db = TablesAdapter.ofNow(request);

		SelectFields selectFields = SelectFields.select()
				.with("dcUsername", "Username")
				.with("dcFirstName", "FirstName")
				.with("dcLastName", "LastName")
				.with("dcEmail", "Email")
				.with("dcPhone", "Phone")
				.with("dcBadges", "Badges")
				.with("dcAddress", "Address")
				.with("dcAddress2", "Address2")
				.with("dcCity", "City")
				.with("dcState", "State")
				.with("dcZip", "Zip");

		callback.returnValue(
				TableUtil.getRecord(db, OperationContext.getOrThrow(), "dcUser", id, selectFields)
		);
	}
}
