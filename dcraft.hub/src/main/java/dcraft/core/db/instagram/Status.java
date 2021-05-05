package dcraft.core.db.instagram;

import dcraft.db.Constants;
import dcraft.db.DatabaseException;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.xml.XElement;

import java.time.ZonedDateTime;

public class Status implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		TablesAdapter db = TablesAdapter.ofNow(request);

		RecordStruct data = request.getDataAsRecord();

		String altcache = data.getFieldAsString("Alt");

		OperationContext ctx = OperationContext.getOrThrow();

		XElement isettings = ApplicationHub.getCatalogSettings("Social-Instagram", altcache);

		if (isettings == null) {
			Logger.warn("Missing Instagram settings.");
			callback.returnEmpty();
			return;
		}

		if (StringUtil.isEmpty(altcache)) {
			altcache = "default";
		}

		try {
			callback.returnValue(RecordStruct.record()
					.with("UserId", isettings.attr("UserId"))
					.with("CacheSize", isettings.getAttributeAsInteger("Cache", 25))
					.with("Token", Struct.objectToString(db.getStaticList("dcTenant", Constants.DB_GLOBAL_ROOT_RECORD, "dcmInstagramAccessToken", altcache)))
					.with("TokenExpire", Struct.objectToDateTime(db.getStaticList("dcTenant", Constants.DB_GLOBAL_ROOT_RECORD, "dcmInstagramAccessExpire", altcache)))
					.with("TokenDisabled", Struct.objectToBooleanOrFalse(db.getStaticList("dcTenant", Constants.DB_GLOBAL_ROOT_RECORD, "dcmInstagramAccessDisabled", altcache)))
					.with("CachedAt", Struct.objectToDateTime(request.getInterface().get(ctx.getTenant().getAlias(), "dcmInstagramWidget", altcache, "Stamp")))
			);
		}
		catch (DatabaseException e) {
			Logger.error("Unable to reset cache");
			callback.returnEmpty();
		}
	}
}
