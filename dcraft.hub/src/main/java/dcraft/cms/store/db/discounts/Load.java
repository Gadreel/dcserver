package dcraft.cms.store.db.discounts;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.request.query.SelectFields;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.util.StringUtil;

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
				.with("dcmWasUsed", "WasUsed")
				.withAs("ProductId", "dcmProduct")
				.withGroup("dcmRuleProduct", "Products", "ProductId",
					SelectFields.select()
							.with("dcmRuleAmount", "Amount")
							.with("dcmRuleMode", "Mode")
				);

		RecordStruct result = TableUtil.getRecord(db, OperationContext.getOrThrow(), "dcmDiscount", id, fields);

		String pid = result.getFieldAsString("ProductId");

		if (StringUtil.isNotEmpty(pid))
			result.with("Product", db.getStaticScalar("dcmProduct", pid, "dcmTitle"));

		ListStruct products = result.getFieldAsList("Products");

		for (int i = 0; i < products.size(); i++) {
			RecordStruct prod = products.getItemAsRecord(i);

			pid = prod.getFieldAsString("ProductId");

			prod.with("Product", db.getStaticScalar("dcmProduct", pid, "dcmTitle"));
		}

		callback.returnValue(result);
	}
}
