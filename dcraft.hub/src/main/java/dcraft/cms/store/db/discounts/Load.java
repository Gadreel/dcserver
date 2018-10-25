package dcraft.cms.store.db.discounts;

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

		// TODO support load by code also

		String id = data.getFieldAsString("Id");

		TablesAdapter db = TablesAdapter.ofNow(request);

		SelectFields fields = SelectFields.select()
				.with("Id")
				.with("dcmTitle", "Title")
				.with("dcmCode", "Code")
				.with("dcmType", "Type")
				.with("dcmMode", "Mode")
				.with("dcmActive", "Active")
				.with("dcmAmount", "Amount")
				.with("dcmMinimumOrder", "MinimumOrder")
				.with("dcmStart", "Start")
				.with("dcmExpire", "Expire")
				.with("dcmAutomatic", "Automatic")
				.with("dcmOneTimeUse", "OneTimeUse")
				.with("dcmWasUsed", "WasUsed");

		callback.returnValue(
				TableUtil.getRecord(db, OperationContext.getOrThrow(), "dcmDiscount", id, fields)
		);
	}
}
