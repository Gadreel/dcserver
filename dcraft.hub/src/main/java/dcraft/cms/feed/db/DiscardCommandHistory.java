package dcraft.cms.feed.db;

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
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.Struct;
import dcraft.util.TimeUtil;

import java.time.ZonedDateTime;

public class DiscardCommandHistory implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		TablesAdapter db = TablesAdapter.ofNow(request);
		
		String feed = request.getDataAsRecord().getFieldAsString("Feed");
		String path = request.getDataAsRecord().getFieldAsString("Path");
		
		CommonPath epath = CommonPath.from("/" + OperationContext.getOrThrow().getSite().getAlias() + "/" + feed + path.substring(0, path.length() - 5));
		
		Unique collector = Unique.unique();
		
		db.traverseIndex(OperationContext.getOrThrow(),"dcmFeedHistory", "dcmPath", epath.toString(), collector.withNested(
				CurrentRecord.current().withNested(HistoryFilter.forDraft())));
		
		String hid = collector.isEmpty() ? null : collector.getOne().toString();
		
		if (hid != null) {
			db.setStaticScalar("dcmFeedHistory", hid, "dcmCancelled", true);
			db.setStaticScalar("dcmFeedHistory", hid, "dcmCancelledAt", TimeUtil.now());
			db.setStaticScalar("dcmFeedHistory", hid, "dcmCancelledBy", OperationContext.getOrThrow().getUserContext().getUserId());
		}
		else {
			Logger.error("Could not find any feed history to discard");
		}
		
		callback.returnEmpty();
	}
}
