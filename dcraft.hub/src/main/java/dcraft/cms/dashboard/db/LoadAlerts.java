package dcraft.cms.dashboard.db;

import dcraft.cms.thread.db.ThreadUtil;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.proc.filter.CurrentRecord;
import dcraft.db.proc.filter.Max;
import dcraft.db.proc.filter.Unique;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;

public class LoadAlerts implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		TablesAdapter db = TablesAdapter.ofNow(request);
		
		ListStruct resp = ListStruct.list();
		Unique collector = Unique.unique();
		
		ThreadUtil.traverseThreadIndex(db, OperationContext.getOrThrow(), "/NoticesPool", "/InBox",
				CurrentRecord.current()
						.withNested(Max.max()
								.withMax(10)
								.withNested(collector)
						)
		);
		
		for (Object vid : collector.getValues()) {
			String id = Struct.objectToString(vid);
			
			resp.with(
					RecordStruct.record()
							.with("Id", id)
							.with("MessageType", db.getStaticScalar("dcmThread", id, "dcmMessageType"))
							.with("Title", db.getStaticScalar("dcmThread", id, "dcmTitle"))
							.with("Modified", db.getStaticScalar("dcmThread", id, "dcmModified"))
							.with("Read", Struct.objectToBooleanOrFalse(db.getStaticList("dcmThread", id, "dcmRead", "/NoticesPool")))
							.with("Attributes", db.getStaticScalar("dcmThread", id, "dcmSharedAttributes"))
			);
		}
		
		callback.returnValue(resp);
	}
}
