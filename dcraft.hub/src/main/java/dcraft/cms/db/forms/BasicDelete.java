package dcraft.cms.db.forms;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.RecordStruct;

public class BasicDelete implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();
		String id = data.getFieldAsString("Id");

		TablesAdapter db = TablesAdapter.of(request);

		TableUtil.retireRecord(db, "dcmBasicCustomForm", id);

		callback.returnEmpty();
	}
}
