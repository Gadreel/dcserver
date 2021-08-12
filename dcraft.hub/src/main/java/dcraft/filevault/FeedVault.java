package dcraft.filevault;

import dcraft.cms.feed.db.FeedUtilDb;
import dcraft.cms.util.FeedUtil;
import dcraft.db.BasicRequestContext;
import dcraft.db.IConnectionManager;
import dcraft.db.fileindex.FileIndexAdapter;
import dcraft.db.tables.TablesAdapter;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileDescriptor;
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.mem.MemoryStoreFile;
import dcraft.filevault.work.FeedSearchWork;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.*;
import dcraft.log.Logger;
import dcraft.script.ScriptHub;
import dcraft.struct.RecordStruct;
import dcraft.task.TaskHub;
import dcraft.util.StringUtil;
import dcraft.web.ui.UIUtil;
import dcraft.xml.*;

import java.util.ArrayList;
import java.util.List;

public class FeedVault extends FileStoreVault {
	// TODO if delete "pages" path also delete the "www" file ?

	@Override
	public void processTransaction(TransactionBase tx) throws OperatingContextException {
		// Feeds needs to be a local store with Expand mode

		// process moves the files
		super.processTransaction(tx);
		
		IConnectionManager connectionManager = ResourceHub.getResources().getDatabases().getDatabase();
		
		TablesAdapter adapter = TablesAdapter.of(BasicRequestContext.of(connectionManager.allocateAdapter()));
		
		FileIndexAdapter fileIndexAdapter = FileIndexAdapter.of(BasicRequestContext.of(connectionManager.allocateAdapter()));
		
		for (CommonPath file : tx.getDeletelist()) {
			FeedUtilDb.deleteFeedIndex(adapter, file);
		}
		
		// clean list does not matter here
		
		for (CommonPath file : tx.getUpdateList()) {
			FeedUtilDb.updateFeedIndex(adapter, file);
			
			TaskHub.submit(
					UIUtil.mockWebRequestTask(this.tenant, this.site, "Feed file search indexing")
							.withWork(FeedSearchWork.of(this, file, fileIndexAdapter))
			);
		}
	}
	
	@Override
	public void getFolderListing(FileDescriptor file, RecordStruct params, OperationOutcome<List<? extends FileDescriptor>> callback) throws OperatingContextException {
		this.fsd.getFolderListing(file.getPathAsCommon(), new OperationOutcome<List<FileStoreFile>>() {
			@Override
			public void callback(List<FileStoreFile> result) throws OperatingContextException {
				
				boolean showHidden = OperationContext.getOrThrow().getUserContext().isTagged("Admin");
				
				List<FileDescriptor> files = new ArrayList<>();
				
				for (FileDescriptor file : this.getResult()) {
					if (file.getName().equals(".DS_Store"))
						continue;
					
					if (! showHidden && file.getName().startsWith("."))
						continue;
					
					// don't list "part" files, just full
					if (StringUtil.countOccurrences(file.getName(), '.') > 1)
						continue;
					
					files.add(file);
				}
				
				callback.returnValue(files);
			}
		});
	}

	@Override
	public void executeCustom(RecordStruct request, OperationOutcomeStruct fcb) throws OperatingContextException {
		String cmd = request.getFieldAsString("Command");
		
		// TODO check security
		if ("AddFeed".equals(cmd)) {
			FeedUtil.addFeed(request.getFieldAsString("Path"), request.getFieldAsRecord("Params"), fcb);
			return;
		}

		if ("LoadPart".equals(cmd)) {
			this.getMappedFileDetail(request.getFieldAsString("Path"), request.getFieldAsRecord("Params"), new OperationOutcome<FileDescriptor>() {
				@Override
				public void callback(FileDescriptor result) throws OperatingContextException {
					if (this.hasErrors()) {
						fcb.returnEmpty();
						return;
					}
					
					if (this.isEmptyResult()) {
						Logger.error("Your request appears valid but does not map to a folder.  Unable to complete.");
						fcb.returnEmpty();
						return;
					}
					
					FileStoreFile fi = (FileStoreFile) this.getResult();
					
					if (!fi.exists()) {
						Logger.error("Your request appears valid but does not map to a folder.  Unable to complete.");
						fcb.returnEmpty();
						return;
					}
					
					fi.readAllText(new OperationOutcome<String>() {
						@Override
						public void callback(String result) throws OperatingContextException {
							if (this.isNotEmptyResult()) {
								// TODO parse as UI
								XElement root = XmlReader.parse(result, true, true);
								
								if (root == null) {
									Logger.error("Feed file not well formed XML");
									fcb.returnEmpty();
									return;
								}
								
								XElement part = root.findId(request.selectAsString("Params.PartId"));
								
								if (part == null) {
									Logger.error("Feed file missing part");
									fcb.returnEmpty();
									return;
								}

								// TODO check to see if authorized to edit this part
								// using a new security method on the UI element
								
								String partpath = request.selectAsString("Params.PartPath", "=");
								
								if (! ("=".equals(partpath) || "_".equals(partpath))) {
									part = part.selectFirst(partpath);
								}
								
								if (part == null) {
									Logger.error("Feed file missing part path");
									fcb.returnEmpty();
									return;
								}
								
								String loadmode = request.selectAsString("Params.Mode", "json");

								if ("json".equals(loadmode)) {
									RecordStruct prt = XmlToJson.convertXml(part, true, false);

									fcb.returnValue(RecordStruct.record()
											.with("Extra", "_".equals(partpath) ? prt.getFieldAsList("children") :  prt)
									);
								}
								else if ("text".equals(loadmode)) {
									fcb.returnValue(RecordStruct.record()
											.with("Extra", "_".equals(partpath) ? part.toInnerString(true) : part.toPrettyString())
									);
								}
								else {
									Logger.error("Invalid load mode");
									fcb.returnEmpty();
								}
							}
							else {
								fcb.returnEmpty();
							}
						}
					});
				}
			});
			
			return;
		}
		
		// TODO check security
		if ("SavePart".equals(cmd) || "SaveMeta".equals(cmd) || "Reorder".equals(cmd)) {
			this.getMappedFileDetail(request.getFieldAsString("Path"), request.getFieldAsRecord("Params"), new OperationOutcome<FileDescriptor>() {
				@Override
				public void callback(FileDescriptor result) throws OperatingContextException {
					if (this.hasErrors()) {
						fcb.returnEmpty();
						return;
					}
					
					if (this.isEmptyResult()) {
						Logger.error("Your request appears valid but does not map to a folder.  Unable to complete.");
						fcb.returnEmpty();
						return;
					}
					
					FileStoreFile fi = (FileStoreFile) this.getResult();
					
					if (!fi.exists()) {
						Logger.error("Your request appears valid but does not map to a folder.  Unable to complete.");
						fcb.returnEmpty();
						return;
					}
					
					fi.readAllText(new OperationOutcome<String>() {
						@Override
						public void callback(String result) throws OperatingContextException {
							if (this.isNotEmptyResult()) {
								XElement root = ScriptHub.parseInstructions(result);

								if (root == null) {
									Logger.error("Feed file not well formed XML");
									fcb.returnEmpty();
									return;
								}
								
								try (OperationMarker om = OperationMarker.create()) {
									FeedUtil.applyCommand(fi.getPathAsCommon(), root, request, true);
									
									if (om.hasErrors()) {
										fcb.returnEmpty();
										return;
									}
								}
								catch (Exception x) {
									Logger.error("OperationMarker error");
									fcb.returnEmpty();
									return;
								}
								
								// save part as deposit
								MemoryStoreFile msource = MemoryStoreFile.of(fi.getPathAsCommon())
										.with(root.toPrettyString());

								VaultUtil.transfer(FeedVault.this.name, msource, fi.getPathAsCommon(), null, fcb);
							}
							else {
								fcb.returnEmpty();
							}
						}
					});
				}
			});
			
			return;
		}
		
		if ("LoadMeta".equals(cmd)) {
			this.getMappedFileDetail(request.getFieldAsString("Path"), request.getFieldAsRecord("Params"), new OperationOutcome<FileDescriptor>() {
				@Override
				public void callback(FileDescriptor result) throws OperatingContextException {
					if (this.hasErrors()) {
						fcb.returnEmpty();
						return;
					}
					
					if (this.isEmptyResult()) {
						Logger.error("Your request appears valid but does not map to a folder.  Unable to complete.");
						fcb.returnEmpty();
						return;
					}
					
					FileStoreFile fi = (FileStoreFile) this.getResult();
					
					if (!fi.exists()) {
						Logger.error("Your request appears valid but does not map to a folder.  Unable to complete.");
						fcb.returnEmpty();
						return;
					}
					
					fi.readAllText(new OperationOutcome<String>() {
						@Override
						public void callback(String result) throws OperatingContextException {
							if (this.isNotEmptyResult()) {
								// TODO parse as UI
								XElement root = XmlReader.parse(result, true, true);
								
								if (root == null) {
									Logger.error("Feed file not well formed XML");
									fcb.returnEmpty();
									return;
								}
								
								RecordStruct info = FeedUtil.metaToInfo(fi.getPathAsCommon().getName(0), request.selectAsString("Params.TrLocale"), root);
								
								fcb.returnValue(RecordStruct.record().with("Extra", info));
							}
							else {
								fcb.returnEmpty();
							}
						}
					});
				}
			});
			
			return;
		}
		
		/* review
		// TODO check security
		if ("IndexFolder".equals(cmd)) {
			this.mapRequest(request, new OperationOutcome<FileStoreFile>() {
				@Override
				public void callback(FileStoreFile result) throws OperatingContextException {
					if (this.hasErrors()) {
						fcb.returnEmpty();
						return;
					}
					
					if (this.isEmptyResult()) {
						Logger.error("Your request appears valid but does not map to a folder.  Unable to complete.");
						fcb.returnEmpty();
						return;
					}
					
					FileStoreFile fi = result;
					
					if (! fi.exists() || ! fi.isFolder()) {
						Logger.error("Your request appears valid but does not map to a folder.  Unable to complete.");
						fcb.returnEmpty();
						return;
					}
					
					fi.getFolderListing(new OperationOutcome<List<FileStoreFile>>() {
						@Override
						public void callback(List<FileStoreFile> result) throws OperatingContextException {
							ListStruct files = ListStruct.list();
							
							for (FileStoreFile file : result)
								files.with(file.getPath());
							
							ServiceHub.call(DataRequest.of("dcmUpdateFeed")
									.withParam("Updated", files),
									fcb
							);
						}
					});
				}
			});
		
			return;
		}
			*/
		
		super.executeCustom(request, fcb);
	}
}
