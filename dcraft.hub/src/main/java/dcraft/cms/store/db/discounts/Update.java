package dcraft.cms.store.db.discounts;

import dcraft.cms.store.db.Util;
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

		String id = data.getFieldAsString("Id");

		TableUtil.updateRecord(db, UpdateRecordRequest.update()
				.withTable("dcmDiscount")
				.withId(id)
				.withConditionallyUpdateFields(data, "Title", "dcmTitle", "Type", "dcmType",
						"Mode", "dcmMode", "Code", "dcmCode", "Amount", "dcmAmount", "MinimumOrder", "dcmMinimumOrder",
						"Start", "dcmStart", "Expire", "dcmExpire", "Automatic", "dcmAutomatic",
						"OneTimeUse", "dcmOneTimeUse", "WasUsed", "dcmWasUsed", "Active", "dcmActive"
				)
		);

		db.updateStaticScalar("dcmDiscount", id, "dcmState", "Check");

		Util.resolveDiscountRules(db);

		if (data.hasField("Active") || data.hasField("Start") || data.hasField("Expire"))
			Util.scheduleDiscountRules(db);

		callback.returnEmpty();
	}
}
