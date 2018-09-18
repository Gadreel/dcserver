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
		
		CommonPath epath = CommonPath.from("/" + OperationContext.getOrThrow().getSite().getAlias() + "/" + feed + path.substring(0, path.length() - 5));
		
		Unique collector = (Unique) db.traverseIndex(OperationContext.getOrThrow(), "dcmFeedHistory", "dcmPath", epath.toString(), Unique.unique().withNested(
				CurrentRecord.current().withNested(HistoryFilter.forDraft())));
		
		String hid = collector.isEmpty() ? null : collector.getOne().toString();
		
		if (hid == null) {
			hid = db.createRecord("dcmFeedHistory");
			
			db.setStaticScalar("dcmFeedHistory", hid, "dcmPath", epath);
			db.setStaticScalar("dcmFeedHistory", hid, "dcmStartedAt", TimeUtil.now());
			db.setStaticScalar("dcmFeedHistory", hid, "dcmStartedBy", OperationContext.getOrThrow().getUserContext().getUserId());
		}
		else {
			db.setStaticScalar("dcmFeedHistory", hid, "dcmModifiedAt", TimeUtil.now());
			db.setStaticScalar("dcmFeedHistory", hid, "dcmModifiedBy", OperationContext.getOrThrow().getUserContext().getUserId());
		}
		
		ListStruct commands = request.getDataAsRecord().getFieldAsList("Commands");
		
		if (commands != null) {
			ZonedDateTime stamp = TimeUtil.now().minusSeconds(1);
			
			for (Struct cstruct : commands.items()) {
				db.setStaticList("dcmFeedHistory", hid, "dcmModifications", TimeUtil.stampFmt.format(stamp), cstruct);
				
				// forward 1 ms
				stamp = stamp.plusNanos(1000000);
			}
		}
		
		// TODO publish - if dcmScheduleAt then just set the dcmScheduled field to dcmScheduleAt, else do it now
		
		callback.returnEmpty();
	}

}
