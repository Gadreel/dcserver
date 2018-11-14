package dcraft.cms.feed.db;

import dcraft.db.proc.IStoredProc;
import dcraft.db.ICallContext;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.ListStruct;

public class Update implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		TablesAdapter db = TablesAdapter.ofNow(request);

		ListStruct updated = request.getDataAsRecord().getFieldAsList("Updated");
		ListStruct deleted = request.getDataAsRecord().getFieldAsList("Deleted");

		if (deleted != null) {
			for (int i = 0; i < deleted.size(); i++) {
				FeedUtilDb.deleteFeedIndex(request.getInterface(), db, deleted.getItemAsString(i));
			}
		}

		if (updated != null) {
			for (int i = 0; i < updated.size(); i++) {
				FeedUtilDb.updateFeedIndex(db, updated.getItemAsString(i));
			}
		}

		callback.returnEmpty();
	}

}
