package dcraft.cms.store.db.settings;

import dcraft.db.Constants;
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

		TablesAdapter db = TablesAdapter.of(request);

		TableUtil.updateRecord(db, UpdateRecordRequest.update()
				.withTable("dcTenant")
				.withId(Constants.DB_GLOBAL_ROOT_RECORD)
				.withConditionallyUpdateFields(data, "StoreInstructionsPickup", "dcmStoreInstructionsPickup",
						"StoreHoursEmail", "dcmStoreHoursEmail", "StoreInstructionsProduct", "dcmStoreInstructionsProduct")
		);

		callback.returnEmpty();
	}
}
