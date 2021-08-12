package dcraft.cms.thread.db;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;

public class LoadMessages implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		TablesAdapter db = TablesAdapter.of(request);
		RecordStruct data = request.getDataAsRecord();

		ListStruct parties = data.getFieldAsList("Parties");
		String folder = data.getFieldAsString("Folder");
		
		callback.returnValue(ThreadUtil.loadMessages(db, parties, folder));
	}
}
