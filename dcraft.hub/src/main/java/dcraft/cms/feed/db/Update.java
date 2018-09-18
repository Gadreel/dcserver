package dcraft.cms.feed.db;

import dcraft.db.DatabaseAdapter;
import dcraft.db.DatabaseException;
import dcraft.db.proc.IStoredProc;
import dcraft.db.ICallContext;
import dcraft.db.tables.TablesAdapter;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStore;
import dcraft.filestore.local.LocalStore;
import dcraft.filevault.Vault;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.time.BigDateTime;
import dcraft.locale.LocaleUtil;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.xml.XElement;
import dcraft.xml.XmlReader;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

public class Update implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		TablesAdapter db = TablesAdapter.ofNow(request);

		ListStruct updated = request.getDataAsRecord().getFieldAsList("Updated");
		ListStruct deleted = request.getDataAsRecord().getFieldAsList("Deleted");

		if (deleted != null) {
			for (int i = 0; i < deleted.size(); i++) {
				FeedUtilDb.delete(request.getInterface(), db, deleted.getItemAsString(i));
			}
		}

		if (updated != null) {
			for (int i = 0; i < updated.size(); i++) {
				FeedUtilDb.update(request.getInterface(), db, updated.getItemAsString(i));
			}
		}

		callback.returnEmpty();
	}

}
