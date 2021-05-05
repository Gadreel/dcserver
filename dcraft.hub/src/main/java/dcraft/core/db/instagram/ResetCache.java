package dcraft.core.db.instagram;

import dcraft.db.DatabaseException;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.request.update.InsertRecordRequest;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.util.TimeUtil;

public class ResetCache implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		TablesAdapter db = TablesAdapter.ofNow(request);

		RecordStruct data = request.getDataAsRecord();

		String altcache = data.getFieldAsString("Alt", "default");

		OperationContext ctx = OperationContext.getOrThrow();

		try {
			request.getInterface().kill(ctx.getTenant().getAlias(), "dcmInstagramWidget", altcache, "Stamp");
		}
		catch (DatabaseException e) {
			Logger.error("Unable to reset cache");
		}

		callback.returnEmpty();
	}
}
