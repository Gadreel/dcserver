package dcraft.cms.thread.db;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.RecordStruct;
import dcraft.util.TimeUtil;

public class Deliver implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();
		
		TablesAdapter db = TablesAdapter.of(request);

		String id = ThreadUtil.getThreadId(db, data);

		ThreadUtil.deliver(db, id, TimeUtil.now(), data.getFieldAsBooleanOrFalse("IndexOnly"));		// TODO handle date time as param

		callback.returnEmpty();
	}
}
