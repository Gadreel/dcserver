package dcraft.core.db.instagram;

import dcraft.db.Constants;
import dcraft.db.DatabaseException;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.util.TimeUtil;

import java.time.ZonedDateTime;

public class ResetToken implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		TablesAdapter db = TablesAdapter.of(request);

		RecordStruct data = request.getDataAsRecord();

		String altcache = data.getFieldAsString("Alt", "default");
		String token = data.getFieldAsString("Token");

		OperationContext ctx = OperationContext.getOrThrow();

		try {
			ZonedDateTime expire = TimeUtil.now().plusDays(2);

			db.updateList("dcTenant", Constants.DB_GLOBAL_ROOT_RECORD, "dcmInstagramAccessToken", altcache, token);
			db.updateList("dcTenant", Constants.DB_GLOBAL_ROOT_RECORD, "dcmInstagramAccessExpire", altcache, expire);
			db.updateList("dcTenant", Constants.DB_GLOBAL_ROOT_RECORD, "dcmInstagramAccessDisabled", altcache, false);

			request.getInterface().kill(ctx.getTenant().getAlias(), "dcmInstagramWidget", altcache, "Stamp");
		}
		catch (DatabaseException e) {
			Logger.error("Unable to reset cache");
		}

		callback.returnEmpty();
	}
}
