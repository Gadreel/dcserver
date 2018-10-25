package dcraft.cms.store.db.discounts;

import dcraft.db.Constants;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.proc.filter.CurrentRecord;
import dcraft.db.proc.filter.Unique;
import dcraft.db.request.query.SelectFields;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;

public class List implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		TablesAdapter db = TablesAdapter.ofNow(request);

		Unique collector = Unique.unique();

		db.traverseRecords(OperationContext.getOrThrow(),"dcmDiscount", CurrentRecord.current().withNested(collector));

		ListStruct result = ListStruct.list();

		SelectFields fields = SelectFields.select()
				.with("Id")
				.with("dcmEntryDate", "EntryDate")
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

		for (Object prod : collector.getValues()) {
			result.with(
					TableUtil.getRecord(db, OperationContext.getOrThrow(), "dcmDiscount", prod.toString(), fields)
			);
		}

		callback.returnValue(result);
	}
}
