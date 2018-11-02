package dcraft.cms.feed.db;

import dcraft.cms.util.FeedUtil;
import dcraft.db.ICallContext;
import dcraft.db.proc.BasicFilter;
import dcraft.db.proc.ExpressionResult;
import dcraft.db.proc.IStoredProc;
import dcraft.db.proc.filter.CurrentRecord;
import dcraft.db.proc.filter.Unique;
import dcraft.db.tables.TablesAdapter;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.mem.MemoryStoreFile;
import dcraft.filevault.FeedVault;
import dcraft.filevault.Vault;
import dcraft.filevault.VaultUtil;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationMarker;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.script.ScriptHub;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.xml.XElement;
import dcraft.xml.XmlReader;

import java.time.ZonedDateTime;

public class SaveCommandHistory implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		TablesAdapter db = TablesAdapter.ofNow(request);
		
		RecordStruct data = request.getDataAsRecord();
		
		String feed = data.getFieldAsString("Feed");
		String path = data.getFieldAsString("Path");
		
		FeedUtilDb.saveHistory(request.getInterface(), db, feed, path, data, data.getFieldAsBooleanOrFalse("Publish"), callback);
	}
}
