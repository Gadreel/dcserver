package dcraft.cms.store.db.discounts;

import dcraft.cms.store.db.Util;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.RecordStruct;

public class RetireRuleProduct implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		String id = data.getFieldAsString("Id");
		String productid = data.getFieldAsString("ProductId");

		TablesAdapter db = TablesAdapter.ofNow(request);

		db.retireStaticList("dcmDiscount", id, "dcmRuleProduct", productid);

		db.updateStaticScalar("dcmDiscount", id, "dcmState", "Check");

		Util.resolveDiscountRules(db);

		callback.returnEmpty();
	}
}
