package dcraft.cms.feed.db;

import dcraft.cms.util.FeedUtil;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.proc.filter.CurrentRecord;
import dcraft.db.proc.filter.Unique;
import dcraft.db.tables.TablesAdapter;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.mem.MemoryStoreFile;
import dcraft.filevault.FileStoreVault;
import dcraft.filevault.Vault;
import dcraft.filevault.VaultUtil;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationMarker;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.script.ScriptHub;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.TimeUtil;
import dcraft.web.ui.inst.ICMSAware;
import dcraft.xml.XElement;
import dcraft.xml.XmlReader;
import dcraft.xml.XmlToJson;

public class LoadPart implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		TablesAdapter db = TablesAdapter.ofNow(request);
		
		RecordStruct data = request.getDataAsRecord();
		
		String feed = data.getFieldAsString("Feed");
		String path = data.getFieldAsString("Path");
		
		CommonPath epath = CommonPath.from("/" + OperationContext.getOrThrow().getSite().getAlias() + "/" + feed + path.substring(0, path.length() - 5));
		
		Unique collector = (Unique) db.traverseIndex(OperationContext.getOrThrow(), "dcmFeedHistory", "dcmPath", epath.toString(), Unique.unique().withNested(
				CurrentRecord.current().withNested(HistoryFilter.forDraft())));
		
		String hid = collector.isEmpty() ? null : collector.getOne().toString();
		
		FileStoreVault feedsvault = OperationContext.getOrThrow().getSite().getFeedsVault();
		
		// TODO feedsvault.mapRequest ...
		
		FileStoreFile fileStoreFile = feedsvault.getFileStore().fileReference(
				CommonPath.from("/" + feed + path));
		
		fileStoreFile.readAllText(new OperationOutcome<String>() {
			@Override
			public void callback(String result) throws OperatingContextException {
				if (this.isNotEmptyResult()) {
					XElement root = ScriptHub.parseInstructions(result);
					
					if (root == null) {
						Logger.error("Feed file not well formed XML");
						callback.returnEmpty();
						return;
					}
					
					if (hid != null) {
						for (String key : db.getStaticListKeys("dcmFeedHistory", hid, "dcmModifications")) {
							RecordStruct command = Struct.objectToRecord(db.getStaticList("dcmFeedHistory", hid, "dcmModifications", key));
							
							// check null, modification could be retired
							if (command != null) {
								try (OperationMarker om = OperationMarker.create()) {
									FeedUtil.applyCommand(epath, root, command, false);
									
									if (om.hasErrors()) {
										// TODO break/skip
									}
								}
								catch (Exception x) {
									Logger.error("OperationMarker error - applying history");
									// TODO break/skip
								}
							}
						}
					}
					
					XElement part = root.findId(data.selectAsString("PartId"));
					
					if (part == null) {
						Logger.error("Feed file missing part");
						callback.returnEmpty();
						return;
					}
					
					//RecordStruct prt = XmlToJson.convertXml(part, true);
					
					if (part instanceof ICMSAware)
						((ICMSAware) part).canonicalize();
					
					callback.returnValue(RecordStruct.record()
							.with("Part", part.toString())
					);
				}
			}
		});
	}

}
