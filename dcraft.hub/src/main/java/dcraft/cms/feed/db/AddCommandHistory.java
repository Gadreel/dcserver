package dcraft.cms.feed.db;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;

public class AddCommandHistory implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		TablesAdapter db = TablesAdapter.of(request);
		
		String feed = request.getDataAsRecord().getFieldAsString("Feed");
		String path = request.getDataAsRecord().getFieldAsString("Path");
		
		FeedUtilDb.addHistory(request.getInterface(), db, feed, path, request.getDataAsRecord().getFieldAsList("Commands"));
		
		callback.returnEmpty();
	}
}
