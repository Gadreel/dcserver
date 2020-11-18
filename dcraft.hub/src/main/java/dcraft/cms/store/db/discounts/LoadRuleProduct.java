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
import dcraft.util.StringUtil;

public class LoadRuleProduct implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		String id = data.getFieldAsString("Id");
		String productid = data.getFieldAsString("ProductId");

		TablesAdapter db = TablesAdapter.ofNow(request);

		String tr = data.getFieldAsString("TrLocale");

		RecordStruct resp = RecordStruct.record();

		if (StringUtil.isEmpty(tr) || tr.equals(OperationContext.getOrThrow().getTenant().getResources().getLocale().getDefaultLocale())) {
			resp.with("Title", db.getStaticScalar("dcmProduct", productid, "dcmTitle"));
		}
		else {
			resp.with("Title", db.getStaticList("dcmProduct", productid, "dcmTitleTr", tr));
		}

		if (db.getStaticList("dcmDiscount", id, "dcmRuleProduct", productid) != null) {
			resp.with("Mode", db.getStaticList("dcmDiscount", id, "dcmRuleMode", productid));
			resp.with("Amount", db.getStaticList("dcmDiscount", id, "dcmRuleAmount", productid));
		}

		callback.returnValue(resp);
	}
}
