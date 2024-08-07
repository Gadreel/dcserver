package dcraft.cms.feed.db;

import dcraft.cms.util.FeedUtil;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.mem.MemoryStoreFile;
import dcraft.filevault.FileStoreVault;
import dcraft.filevault.VaultUtil;
import dcraft.hub.op.*;
import dcraft.log.Logger;
import dcraft.script.ScriptHub;
import dcraft.struct.BaseStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.xml.XElement;

public class SaveMeta implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		TablesAdapter db = TablesAdapter.of(request);
		
		RecordStruct data = request.getDataAsRecord();
		
		String feed = data.getFieldAsString("Feed");
		String path = data.getFieldAsString("Path");
		
		CommonPath epath = CommonPath.from("/" + OperationContext.getOrThrow().getSite().getAlias() + "/" + feed + path.substring(0, path.length() - 5));

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
						public void callback(BaseStruct result) throws OperatingContextException {
							callback.returnEmpty();
						}
					});
				}
			}
		});
	}

}
