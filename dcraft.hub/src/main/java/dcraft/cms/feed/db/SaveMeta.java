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
import dcraft.filevault.FeedVault;
import dcraft.filevault.Vault;
import dcraft.filevault.VaultUtil;
import dcraft.hub.op.*;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.xml.XElement;
import dcraft.xml.XmlReader;

public class SaveMeta implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		TablesAdapter db = TablesAdapter.ofNow(request);
		
		RecordStruct data = request.getDataAsRecord();
		
		String feed = data.getFieldAsString("Feed");
		String path = data.getFieldAsString("Path");
		
		CommonPath epath = CommonPath.from("/" + OperationContext.getOrThrow().getSite().getAlias() + "/" + feed + path.substring(0, path.length() - 5));

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

					try (OperationMarker om = OperationMarker.create()) {
						FeedUtil.applyCommand(epath, root, data.with("Command", "SaveMeta"), false);

						if (om.hasErrors()) {
							callback.returnEmpty();
							return;
						}
					}
					catch (Exception x) {
						Logger.error("OperationMarker error");
						callback.returnEmpty();
						return;
					}

					// save part as deposit
					MemoryStoreFile msource = MemoryStoreFile.of(fileStoreFile.getPathAsCommon())
							.with(root.toPrettyString());

					VaultUtil.transfer(feedsvault.getName(), msource, fileStoreFile.getPathAsCommon(), null, new OperationOutcomeStruct() {
						@Override
						public void callback(Struct result) throws OperatingContextException {
							callback.returnEmpty();
						}
					});
				}
			}
		});
	}

}
