package dcraft.cms.feed.db;

import dcraft.cms.util.FeedUtil;
import dcraft.db.ICallContext;
import dcraft.db.proc.BasicFilter;
import dcraft.db.proc.ExpressionResult;
import dcraft.db.proc.IStoredProc;
import dcraft.db.proc.filter.CurrentRecord;
import dcraft.db.proc.filter.Unique;
import dcraft.db.tables.TablesAdapter;
import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationMarker;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.TimeUtil;

import java.time.ZonedDateTime;

public class AddCommandHistory implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		TablesAdapter db = TablesAdapter.ofNow(request);
		
		String feed = request.getDataAsRecord().getFieldAsString("Feed");
		String path = request.getDataAsRecord().getFieldAsString("Path");
		
		FeedUtilDb.addHistory(request.getInterface(), db, feed, path, request.getDataAsRecord().getFieldAsList("Commands"));
		
		callback.returnEmpty();
	}
}
