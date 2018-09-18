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
		
		CommonPath epath = CommonPath.from("/" + OperationContext.getOrThrow().getSite().getAlias() + "/" + feed + path.substring(0, path.length() - 5));
		
		Unique collector = (Unique) db.traverseIndex(OperationContext.getOrThrow(), "dcmFeedHistory", "dcmPath", epath.toString(), Unique.unique().withNested(
				CurrentRecord.current().withNested(HistoryFilter.forDraft())));
		
		String hid = collector.isEmpty() ? null : collector.getOne().toString();
		
		if (hid == null) {
			hid = db.createRecord("dcmFeedHistory");
			
			db.setStaticScalar("dcmFeedHistory", hid, "dcmPath", epath);
			db.setStaticScalar("dcmFeedHistory", hid, "dcmStartedAt", TimeUtil.now());
			db.setStaticScalar("dcmFeedHistory", hid, "dcmStartedBy", OperationContext.getOrThrow().getUserContext().getUserId());
		}
		else {
			db.setStaticScalar("dcmFeedHistory", hid, "dcmModifiedAt", TimeUtil.now());
			db.setStaticScalar("dcmFeedHistory", hid, "dcmModifiedBy", OperationContext.getOrThrow().getUserContext().getUserId());
		}
		
		String fhid = hid;
		
		if (data.hasField("Note"))
			db.setStaticScalar("dcmFeedHistory", hid, "dcmNote", data.getFieldAsString("Note"));
		
		if (data.getFieldAsBooleanOrFalse("Publish")) {
			db.setStaticScalar("dcmFeedHistory", hid, "dcmPublished", true);
			
			Vault feedsvault = OperationContext.getOrThrow().getSite().getVault("Feeds");
			
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
						
						for (String key : db.getStaticListKeys("dcmFeedHistory", fhid, "dcmModifications")) {
							RecordStruct command = Struct.objectToRecord(db.getStaticList("dcmFeedHistory", fhid, "dcmModifications", key));
							
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
						
						// save part as deposit
						MemoryStoreFile msource = MemoryStoreFile.of(fileStoreFile.getPathAsCommon())
								.with(root.toPrettyString());
						
						VaultUtil.transfer("Feeds", msource, fileStoreFile.getPathAsCommon(), null, new OperationOutcomeStruct() {
							@Override
							public void callback(Struct result) throws OperatingContextException {
								if (! this.hasErrors()) {
									db.setStaticScalar("dcmFeedHistory", fhid, "dcmCompleted", true);
									db.setStaticScalar("dcmFeedHistory", fhid, "dcmCompletedAt", TimeUtil.now());
									
									// TODO publish - if dcmScheduleAt then just set the dcmScheduled field to dcmScheduleAt, else do it now
								}
								
								callback.returnEmpty();
							}
						});
					}
					else {
						Logger.error("Unable to publish to empty file");
						callback.returnEmpty();
					}
				}
			});
		}
		else {
			callback.returnEmpty();
		}
	}

}
