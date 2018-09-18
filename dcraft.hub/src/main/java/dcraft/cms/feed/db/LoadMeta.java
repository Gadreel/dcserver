package dcraft.cms.feed.db;

import dcraft.cms.util.FeedUtil;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.proc.filter.CurrentRecord;
import dcraft.db.proc.filter.Unique;
import dcraft.db.tables.TablesAdapter;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStoreFile;
import dcraft.filevault.Vault;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationMarker;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.xml.XElement;
import dcraft.xml.XmlReader;
import dcraft.xml.XmlToJson;

public class LoadMeta implements IStoredProc {
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
		
		Vault feedsvault = OperationContext.getOrThrow().getSite().getVault("Feeds");
		
		// TODO feedsvault.mapRequest ...
		
		FileStoreFile fileStoreFile = feedsvault.getFileStore().fileReference(
				CommonPath.from("/" + feed + path));
		
		fileStoreFile.readAllText(new OperationOutcome<String>() {
			@Override
			public void callback(String result) throws OperatingContextException {
				if (this.isNotEmptyResult()) {
					// TODO parse as UI
					XElement root = XmlReader.parse(result, true, true);
					
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
					
					
					RecordStruct info = FeedUtil.metaToInfo(feed, data.selectAsString("Params.TrLocale"), root);
					
					callback.returnValue(info);
				}
			}
		});
	}

}
