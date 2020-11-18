package dcraft.cms.store.db.discounts;

import dcraft.cms.store.db.Util;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.RecordStruct;
import dcraft.util.StringUtil;

public class UpdateRuleProduct implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		String id = data.getFieldAsString("Id");
		String productid = data.getFieldAsString("ProductId");

		TablesAdapter db = TablesAdapter.ofNow(request);

		db.updateStaticList("dcmDiscount", id, "dcmRuleProduct", productid, productid);

		if (data.hasField("Mode"))
			db.updateStaticList("dcmDiscount", id, "dcmRuleMode", productid, data.getFieldAsString("Mode"));

		if (data.hasField("Amount"))
			db.updateStaticList("dcmDiscount", id, "dcmRuleAmount", productid, data.getFieldAsString("Amount"));

		db.updateStaticScalar("dcmDiscount", id, "dcmState", "Check");

		Util.resolveDiscountRules(db);

		callback.returnEmpty();
	}
}
