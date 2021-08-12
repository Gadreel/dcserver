package dcraft.cms.dashboard.db;

import dcraft.cms.thread.db.ThreadUtil;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.ListStruct;

public class ArchiveAlerts implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		ListStruct data = request.getDataAsList();

		TablesAdapter db = TablesAdapter.of(request);

		for (int i = 0; i < data.size(); i++)
			ThreadUtil.updateFolder(db, data.getItemAsString(i), "/NoticesPool", "/Archive", true);
		
		callback.returnEmpty();
	}
}
