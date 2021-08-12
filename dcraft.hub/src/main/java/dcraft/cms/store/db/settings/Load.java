package dcraft.cms.store.db.settings;

import dcraft.db.Constants;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.request.query.SelectFields;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.RecordStruct;

public class Load implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		TablesAdapter db = TablesAdapter.of(request);

		SelectFields fields = SelectFields.select()
				.withAs("StoreInstructionsProduct", "dcmStoreInstructionsProduct")
				.withAs("StoreInstructionsPickup", "dcmStoreInstructionsPickup")
				.withAs("StoreHoursEmail", "dcmStoreHoursEmail");

		RecordStruct result = TableUtil.getRecord(db, OperationContext.getOrThrow(), "dcTenant", Constants.DB_GLOBAL_ROOT_RECORD, fields);

		callback.returnValue(result);
	}
}
