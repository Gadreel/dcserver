package dcraft.cms.dashboard.db;

import dcraft.cms.thread.db.ThreadUtil;
import dcraft.core.db.UserDataUtil;
import dcraft.db.DatabaseAdapter;
import dcraft.db.DatabaseException;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.proc.call.SignIn;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;

import java.time.ZonedDateTime;
import java.util.List;

public class ArchiveAlerts implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		ListStruct data = request.getDataAsList();

		TablesAdapter db = TablesAdapter.ofNow(request);

		for (int i = 0; i < data.size(); i++)
			ThreadUtil.updateFolder(db, data.getItemAsString(i), "/NoticesPool", "/Archive", true);
		
		callback.returnEmpty();
	}
}
