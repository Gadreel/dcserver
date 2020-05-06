package dcraft.cms.store.db.products;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.request.update.UpdateRecordRequest;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;

public class UpdateCustomFieldPositions implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		ListStruct data = request.getDataAsList();

		TablesAdapter db = TablesAdapter.ofNow(request);

		for (int i = 0; i < data.size(); i++ ) {
			RecordStruct field = data.getItemAsRecord(i);

			String id = field.getFieldAsString("Id");

			db.updateStaticScalar("dcmProductCustomFields", id, "dcmPosition",
					field.getField("Position"));
		}

		callback.returnEmpty();
	}
}
