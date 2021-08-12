package dcraft.cms.thread.db;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.RecordStruct;

public class AddContent implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();
		
		TablesAdapter db = TablesAdapter.of(request);

		String id = ThreadUtil.getThreadId(db, data);

		String content = data.getFieldAsString("Content");
		String contenttype = data.getFieldAsString("ContentType");	// defaults to unsafe
		String originator = data.getFieldAsString("Originator");		// defaults to current user

		ThreadUtil.addContent(db, id, content, contenttype, originator, null, null);

		callback.returnEmpty();
	}
}
