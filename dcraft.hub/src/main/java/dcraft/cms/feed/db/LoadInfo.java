package dcraft.cms.feed.db;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.*;
import dcraft.struct.RecordStruct;

public class LoadInfo implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		TablesAdapter db = TablesAdapter.of(request);
		
		RecordStruct data = request.getDataAsRecord();
		
		String feed = data.getFieldAsString("Feed");
		String path = data.getFieldAsString("Path");

		RecordStruct result = RecordStruct.record()
				.with("FeedId", FeedUtilDb.pathToId(db,"/" + feed + path, false));

		callback.returnValue(result);
	}
}
