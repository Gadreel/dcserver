package dcraft.filevault;

import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.mem.MemoryStoreFile;
import dcraft.hub.op.*;
import dcraft.log.Logger;
import dcraft.stream.file.MemorySourceStream;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;
import dcraft.xml.*;

import java.util.List;

public class FeedVault extends Vault {
	@Override
	public void listFiles(RecordStruct request, boolean checkAuth, OperationOutcomeStruct fcb) throws OperatingContextException {
		// check bucket security
		if (checkAuth && ! this.checkReadAccess()) {
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
		if (checkAuth && ! this.checkWriteAccess()) {
			Logger.errorTr(434);
			fcb.returnEmpty();
			return;
		}

		String cmd = request.getFieldAsString("Command");
		
		// TODO check security
		if ("AddPage".equals(cmd)) {
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

					Vault tempvault = OperationContext.getOrThrow().getSite().getVault("Templates");

					if (tempvault == null) {
						Logger.error("Template vault missing.");
						fcb.returnEmpty();
						return;
					}

					// TODO future copy all the feed*.html files over
					String temppath = "/" + fi.getPathAsCommon().getName(0)
							+ "/" + request.selectAsString("Params.Template") + "/feed.html";

					// only for "pages" feed
					String wwwpath = "/" + fi.getPathAsCommon().getName(0)
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

											ListStruct fields = request.selectAsList("Params.SetFields");

											if (fields != null) {
												for (Struct fld : fields.items()) {
													if (fld instanceof RecordStruct) {
														RecordStruct recfld = (RecordStruct) fld;

														root.add(0,
															XElement.tag("Meta")
																.attr("Name", recfld.getFieldAsString("Name"))
																.attr("Value", recfld.getFieldAsString("Value"))
														);
													}
												}
											}

											MemoryStoreFile msource = MemoryStoreFile.of(CommonPath.from(temppath))
												.with(root.toPrettyString());

											VaultUtil.transfer("Feeds", msource, fi.getPathAsCommon(),
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

																				VaultUtil.transfer("Web", wwwsource, fi.getPathAsCommon().subpath(1), fcb);
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
		if ("SavePart".equals(cmd)) {
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
								XElement newel = null;

								if ("json".equals(loadmode)) {
									RecordStruct newpart = null;
									
									if ("_".equals(partpath)) {
										ListStruct extra = request.selectAsList("Params.Part");
										
										newpart = RecordStruct.record()
												.with("type", "element")
												.with("name", "dummy")
												.with("children", extra);
									}
									else {
										newpart = request.selectAsRecord("Params.Part");
									}
									
									if (newpart == null) {
										Logger.error("Missing new part content");
										fcb.returnEmpty();
										return;
									}

									// TODO use UI parser
									newel = JsonToXml.convertJson(newpart);
								}
								else if ("text".equals(loadmode)) {
									String newpart = request.selectAsString("Params.Part");

									if (newpart == null) {
										Logger.error("Missing new part content");
										fcb.returnEmpty();
										return;
									}
									
									if ("_".equals(partpath)) {
										newpart = "<dummy>" + newpart + "</dummy>";
									}
									
									// TODO use UI parser
									newel = XmlReader.parse(newpart, true, true);
								}
								else {
									Logger.error("Invalid save mode");
									fcb.returnEmpty();
									return;
								}

								if (newel == null) {
									Logger.error("New part content is not valid xml");
									fcb.returnEmpty();
									return;
								}

								// TODO check that the changes made are allowed - e.g. on TextWidget
								// an Editor cannot change to Unsafe mode

								if ("_".equals(partpath)) {
									part.replaceChildren(newel);
								}
								else {
									part.replace(newel);
								}

								// save part as deposit
								MemoryStoreFile msource = MemoryStoreFile.of(fi.getPathAsCommon())
										.with(root.toPrettyString());

								VaultUtil.transfer(FeedVault.this.name, msource, fi.getPathAsCommon(), fcb);

								/*
								fi.writeAllText(root.toPrettyString(), new OperationOutcomeEmpty() {
									@Override
									public void callback() {
										fcb.returnEmpty();
									}
								});
								*/
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
								
								XElement metas = XElement.tag("dummy");
								
								for (XElement meta : root.selectAll("Meta"))
									metas.add(meta);
								
								RecordStruct prt = XmlToJson.convertXml(metas, true);
								
								fcb.returnValue(RecordStruct.record()
										.with("Extra", prt.getFieldAsList("children"))
								);
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
		if ("SaveMeta".equals(cmd)) {
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

								ListStruct metalist = request.selectAsList("Params.Meta");
								
								if (metalist == null) {
									Logger.error("Missing meta content");
									fcb.returnEmpty();
									return;
								}
								
								// TODO use UI parser
								XElement newel = JsonToXml.convertJson(RecordStruct.record()
										.with("type", "element")
										.with("name", "dummy")
										.with("children", metalist));
								
								if (newel == null) {
									Logger.error("New part content is not valid xml");
									fcb.returnEmpty();
									return;
								}
								
								int idxlastmeta = 0;
								
								for (int i = 0; i < root.getChildCount(); i++) {
									XNode node = root.getChild(i);
									
									if ((node instanceof XElement) && "Meta".equals(((XElement)node).getName()))
										idxlastmeta = i;
								}
								
								for (XElement newmeta : newel.selectAll("Meta")) {
									String name = newmeta.getAttribute("Name");
									
									if (StringUtil.isEmpty(name))
										continue;
									
									boolean fnd = false;
									
									for (XElement oldmeta : root.selectAll("Meta")) {
										if (name.equals(oldmeta.getAttribute("Name"))) {
											oldmeta.replace(newmeta);
											fnd = true;
											break;
										}
									}
									
									if (! fnd) {
										root.add(idxlastmeta, newmeta);
										idxlastmeta++;
									}
								}

								// save part as deposit
								MemoryStoreFile msource = MemoryStoreFile.of(fi.getPathAsCommon())
										.with(root.toPrettyString());

								VaultUtil.transfer(FeedVault.this.name, msource, fi.getPathAsCommon(), fcb);

								/*
								fi.writeAllText(root.toPrettyString(), new OperationOutcomeEmpty() {
									@Override
									public void callback() {
										fcb.returnEmpty();
									}
								});
								*/
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
		
		super.executeCustom(request, checkAuth, fcb);
	}
}
