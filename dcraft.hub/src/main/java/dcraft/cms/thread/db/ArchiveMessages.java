package dcraft.cms.thread.db;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.Struct;

import java.util.List;

public class ArchiveMessages implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		ListStruct ids = request.getDataAsList();

		TablesAdapter adapter = TablesAdapter.ofNow(request);

		for (Struct sid : ids.items()) {
			String tid = sid.toString();

			List<String> access = ThreadUtil.collectMessageAccess(adapter, OperationContext.getOrThrow(), tid);

			for (String party : access)
				ThreadUtil.updateFolder(adapter, tid, party, "/Archive", false);
		}

		callback.returnEmpty();
	}
}
