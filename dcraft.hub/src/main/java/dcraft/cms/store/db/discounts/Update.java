package dcraft.cms.store.db.discounts;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.request.update.UpdateRecordRequest;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.RecordStruct;

public class Update implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		TablesAdapter db = TablesAdapter.ofNow(request);

		TableUtil.updateRecord(db, UpdateRecordRequest.update()
				.withTable("dcmDiscount")
				.withId(data.getFieldAsString("Id"))
				.withConditionallyUpdateFields(data, "Title", "dcmTitle", "Type", "dcmType",
						"Mode", "dcmMode", "Code", "dcmCode", "Amount", "dcmAmount", "MinimumOrder", "dcmMinimumOrder",
						"Start", "dcmStart", "Expire", "dcmExpire", "Automatic", "dcmAutomatic",
						"OneTimeUse", "dcmOneTimeUse", "WasUsed", "dcmWasUsed", "Active", "dcmActive"
				)
		);

		callback.returnEmpty();
	}
}
