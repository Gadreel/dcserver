package dcraft.cms.store.db.discounts;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.RecordStruct;
import dcraft.util.StringUtil;

public class LoadRuleProduct implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		String id = data.getFieldAsString("Id");
		String productid = data.getFieldAsString("ProductId");

		TablesAdapter db = TablesAdapter.of(request);

		String tr = data.getFieldAsString("TrLocale");

		RecordStruct resp = RecordStruct.record();

		if (StringUtil.isEmpty(tr) || tr.equals(OperationContext.getOrThrow().getTenant().getResources().getLocale().getDefaultLocale())) {
			resp.with("Title", db.getScalar("dcmProduct", productid, "dcmTitle"));
		}
		else {
			resp.with("Title", db.getList("dcmProduct", productid, "dcmTitleTr", tr));
		}

		if (db.getList("dcmDiscount", id, "dcmRuleProduct", productid) != null) {
			resp.with("Mode", db.getList("dcmDiscount", id, "dcmRuleMode", productid));
			resp.with("Amount", db.getList("dcmDiscount", id, "dcmRuleAmount", productid));
		}

		callback.returnValue(resp);
	}
}
