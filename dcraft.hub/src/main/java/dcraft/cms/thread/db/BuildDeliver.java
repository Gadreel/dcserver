package dcraft.cms.thread.db;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.RecordStruct;
import dcraft.util.TimeUtil;

public class BuildDeliver implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();
		
		TablesAdapter db = TablesAdapter.ofNow(request);

		String id = ThreadUtil.getThreadId(db, data);

		ThreadUtil.buildContentAndDeliver(id);

		callback.returnEmpty();
	}
}
