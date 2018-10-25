package dcraft.cms.store.db.discounts;

import dcraft.db.Constants;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.request.update.InsertRecordRequest;
import dcraft.db.request.update.UpdateRecordRequest;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.TimeUtil;

public class Add implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		TablesAdapter db = TablesAdapter.ofNow(request);

		String newid = TableUtil.updateRecord(db, InsertRecordRequest.insert()
				.withTable("dcmDiscount")
				.withConditionallyUpdateFields(data, "Title", "dcmTitle", "Type", "dcmType",
						"Mode", "dcmMode", "Code", "dcmCode", "Amount", "dcmAmount", "MinimumOrder", "dcmMinimumOrder",
						"Start", "dcmStart", "Expire", "dcmExpire", "Automatic", "dcmAutomatic",
						"OneTimeUse", "dcmOneTimeUse", "WasUsed", "dcmWasUsed", "Active", "dcmActive"
				)
				.withSetField("dcmEntryDate", TimeUtil.now())
		);

		callback.returnValue(
				RecordStruct.record()
					.with("Id", newid)
		);
	}
}
