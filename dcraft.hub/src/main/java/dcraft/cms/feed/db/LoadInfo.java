package dcraft.cms.feed.db;

import dcraft.cms.util.FeedUtil;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.proc.filter.CurrentRecord;
import dcraft.db.proc.filter.Unique;
import dcraft.db.tables.TablesAdapter;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStoreFile;
import dcraft.filevault.FileStoreVault;
import dcraft.hub.op.*;
import dcraft.log.Logger;
import dcraft.script.ScriptHub;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.web.ui.inst.ICMSAware;
import dcraft.xml.XElement;

public class LoadInfo implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		TablesAdapter db = TablesAdapter.ofNow(request);
		
		RecordStruct data = request.getDataAsRecord();
		
		String feed = data.getFieldAsString("Feed");
		String path = data.getFieldAsString("Path");

		RecordStruct result = RecordStruct.record()
				.with("FeedId", FeedUtilDb.pathToId(db,"/" + feed + path, false));

		callback.returnValue(result);
	}
}
