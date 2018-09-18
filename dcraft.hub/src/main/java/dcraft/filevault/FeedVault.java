package dcraft.filevault;

import dcraft.cms.util.FeedUtil;
import dcraft.db.request.DataRequest;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStore;
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.local.LocalStore;
import dcraft.filestore.mem.MemoryStoreFile;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.*;
import dcraft.log.Logger;
import dcraft.service.ServiceHub;
import dcraft.stream.file.MemorySourceStream;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.FileUtil;
import dcraft.util.StringUtil;
import dcraft.xml.*;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.List;

public class FeedVault extends Vault {

	public void commitFiles(Transaction tx) throws OperatingContextException {
		ListStruct deletes = ListStruct.list();
		ListStruct updates = ListStruct.list();

		FileStore vfs = this.getFileStore();

		if (vfs instanceof LocalStore) {
			for (CommonPath delete : tx.getDeletelist())
				deletes.with(delete);

			Path source = tx.getFolder().getPath();

			try {
				if (Files.exists(source)) {
					Files.walkFileTree(source, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
							new SimpleFileVisitor<Path>() {
								@Override
								public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
									if (file.endsWith(".DS_Store"))
										return FileVisitResult.CONTINUE;

									Path dest = source.relativize(file);

									updates.with(CommonPath.from("/" + dest.toString()));

									return FileVisitResult.CONTINUE;
								}
							});
				}
			}
			catch (IOException x) {
				Logger.error("Error copying file tree: " + x);
			}

		}

		super.commitFiles(tx);

		ServiceHub.call(DataRequest.of("dcmUpdateFeed")
				.withParam("Updated", updates)
				.withParam("Deleted" ,deletes)
		);
	}

	@Override
	public void listFiles(RecordStruct request, boolean checkAuth, OperationOutcomeStruct fcb) throws OperatingContextException {
		// check bucket security
		if (checkAuth && ! this.checkReadAccess("ListFiles", request)) {
			Logger.errorTr(434);
			fcb.returnEmpty();
			return;
		}
		
		this.mapRequest(request, new OperationOutcome<FileStoreFile>() {
			@Override
			public void callback(FileStoreFile result) throws OperatingContextException {
				if (this.hasErrors()) {
					fcb.returnEmpty();
					return;
				}
				
				if (this.isEmptyResult()) {
					Logger.error("Your request appears valid but does not map to a file.  Unable to complete.");
					fcb.returnEmpty();
					return;
				}
				
				FileStoreFile fi = this.getResult();
				
				if (!fi.exists()) {
					fcb.returnEmpty();
					return;
				}

				FeedVault.this.fsd.getFolderListing(fi.getPathAsCommon(), new OperationOutcome<List<FileStoreFile>>() {
					@Override
					public void callback(List<FileStoreFile> result) throws OperatingContextException {
						if (this.hasErrors()) {
							fcb.returnEmpty();
							return;					
						}
						
						boolean showHidden = OperationContext.getOrThrow().getUserContext().isTagged("Admin");
						
						ListStruct files = new ListStruct();
						
						for (FileStoreFile file : this.getResult()) {
							if (file.getName().equals(".DS_Store"))
								continue;
							
							if (! showHidden && file.getName().startsWith("."))
								continue;

							// TODO better to put this in the browser UI
							//boolean isHtml = file.getName().endsWith(".html");

							//if (! file.isFolder() && ! isHtml)
							//	continue;

							// don't list "part" files, just full
							if (StringUtil.countOccurrences(file.getName(), '.') > 1)
								continue;

							RecordStruct fdata = new RecordStruct();
							
							//fdata.with("FileName", isHtml ? file.getName().substring(0, file.getName().length() - 5) : file.getName());
							fdata.with("FileName", file.getName());
							fdata.with("IsFolder", file.isFolder());
							fdata.with("Modified", file.getModification());
							fdata.with("Size", file.getSize());
							fdata.with("Extra", file.getExtra());
							
							files.with(fdata);
						}
						
						fcb.returnValue(files);
					}
				});
			}
		});
	}	

	@Override
	public void executeCustom(RecordStruct request, boolean checkAuth, OperationOutcomeStruct fcb) throws OperatingContextException {
		// check bucket security
		if (checkAuth && ! this.checkWriteAccess("Custom", request)) {
			Logger.errorTr(434);
			fcb.returnEmpty();
			return;
		}

		String cmd = request.getFieldAsString("Command");
		
		// TODO check security
		if ("AddFeed".equals(cmd)) {
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

					if (fi.exists()) {
						Logger.error("This path would overwrite an existing page. Please edit the page or remove it and then add it.");
						fcb.returnEmpty();
						return;
					}

					Vault tempvault = OperationContext.getOrThrow().getSite().getVault("SiteFiles");

					if (tempvault == null) {
						Logger.error("SiteFiles vault missing.");
						fcb.returnEmpty();
						return;
					}

					// TODO future copy all the feed*.html files over
					String temppath = "/templates/" + fi.getPathAsCommon().getName(0)
							+ "/" + request.selectAsString("Params.Template") + "/feed.html";

					// only for "pages" feed
					String wwwpath = "/templates/" + fi.getPathAsCommon().getName(0)
							+ "/" + request.selectAsString("Params.Template") + "/www.html";

					tempvault.mapRequest(RecordStruct.record()
							.with("Path", temppath),
							new OperationOutcome<FileStoreFile>() {
								@Override
								public void callback(FileStoreFile source) throws OperatingContextException {
									if (this.hasErrors() || this.isEmptyResult()) {
										Logger.error("Template file missing.");
										fcb.returnEmpty();
										return;
									}

									source.readAllText(new OperationOutcome<String>() {
										@Override
										public void callback(String result) throws OperatingContextException {
											if (this.hasErrors() || this.isEmptyResult()) {
												Logger.error("Template file is empty.");
												fcb.returnEmpty();
												return;
											}

											XElement root = XmlReader.parse(result, false, true);

											if (root == null) {
												Logger.error("Template file is not well formed.");
												fcb.returnEmpty();
												return;
											}

											RecordStruct def = FeedUtil.getFeedDefinition(fi.getPathAsCommon().getName(0));

											// feeds use site default
											String defloc = OperationContext.getOrThrow().getSite().getResources().getLocale().getDefaultLocale();

											String currloc = request.selectAsString("Params.TrLocale", ResourceHub.getResources().getLocale().getDefaultLocale());

											ListStruct fields = request.selectAsList("Params.SetFields");

											if (fields != null) {
												for (Struct fld : fields.items()) {
													if (fld instanceof RecordStruct) {
														RecordStruct recfld = (RecordStruct) fld;

														FeedUtil.updateField(
																def,
																recfld.getFieldAsString("Name"),
																recfld.getFieldAsString("Value"),
																root,
																currloc,
																defloc
														);
													}
												}
											}

											MemoryStoreFile msource = MemoryStoreFile.of(CommonPath.from(temppath))
												.with(root.toPrettyString());

											VaultUtil.transfer("Feeds", msource, fi.getPathAsCommon(), null,
													new OperationOutcomeStruct() {
														@Override
														public void callback(Struct result) throws OperatingContextException {
															if ("pages".equals(fi.getPathAsCommon().getName(0))) {
																tempvault.mapRequest(RecordStruct.record()
																				.with("Path", wwwpath),
																		new OperationOutcome<FileStoreFile>() {
																			@Override
																			public void callback(FileStoreFile wwwsource) throws OperatingContextException {
																				if (this.hasErrors() || this.isEmptyResult()) {
																					Logger.error("Template web file missing.");
																					fcb.returnEmpty();
																					return;
																				}

																				CommonPath sitepath = CommonPath.from("/www").resolve(fi.getPathAsCommon().subpath(1));

																				VaultUtil.transfer("SiteFiles", wwwsource, sitepath, null, fcb);
																			}
																		});
															}
															else {
																fcb.returnEmpty();
															}
														}
													});
										}
									});
								}
							});
				}
			});

			return;
		}

		if ("LoadPart".equals(cmd)) {
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
					
					FileStoreFile fi = this.getResult();
					
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
									RecordStruct prt = XmlToJson.convertXml(part, true);

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
					
					FileStoreFile fi = this.getResult();
					
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
					
					FileStoreFile fi = this.getResult();
					
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
		
		super.executeCustom(request, checkAuth, fcb);
	}
}
