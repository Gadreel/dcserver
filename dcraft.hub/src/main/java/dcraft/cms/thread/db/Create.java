package dcraft.cms.thread.db;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.RecordStruct;

public class Create implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();
		
		TablesAdapter db = TablesAdapter.ofNow(request);

		String id = ThreadUtil.createThread(db, data.getFieldAsString("Title"), data.getFieldAsString("Type"),
				data.getFieldAsString("From"));

		RecordStruct shared = data.getFieldAsRecord("SharedAttributes");

		if (shared != null) {
			db.setStaticScalar("dcmThread", id, "dcmSharedAttributes", shared);
		}

		callback.returnValue(RecordStruct.record()
				.with("Id", id)
				.with("Uuid", db.getStaticScalar("dcmThread", id, "dcmUuid"))
		);
	}
}
