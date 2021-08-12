package dcraft.core.db.instagram;

import dcraft.db.DatabaseException;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;

public class ClearCache implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		TablesAdapter db = TablesAdapter.of(request);

		RecordStruct data = request.getDataAsRecord();

		String altcache = data.getFieldAsString("Alt", "default");

		OperationContext ctx = OperationContext.getOrThrow();

		try {
			request.getInterface().kill(ctx.getTenant().getAlias(), "dcmInstagramWidget", altcache);
		}
		catch (DatabaseException e) {
			Logger.error("Unable to reset cache");
		}

		callback.returnEmpty();
	}
}
