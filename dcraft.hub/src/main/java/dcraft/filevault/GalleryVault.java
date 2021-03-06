package dcraft.filevault;

import java.util.ArrayList;
import java.util.List;

import dcraft.cms.util.GalleryUtil;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileDescriptor;
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.mem.MemoryStoreFile;
import dcraft.hub.op.*;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;

public class GalleryVault extends FileStoreVault {
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
					
					if (! file.isFolder())
						continue;
					
					boolean isImage = file.getName().endsWith(".v");
					
					if (isImage) {
						String path = file.getPath();
						
						file.with("Path", path.substring(0, path.length() - 2));
						file.with("IsFolder", false);

						// TODO set modified and size based on the `original` or 'full' variation
					}
					
					files.add(file);
				}
				
				callback.returnValue(files);
			}
		});
	}

	@Override
	public void executeCustom(RecordStruct request, OperationOutcomeStruct fcb) throws OperatingContextException {
		String cmd = request.getFieldAsString("Command");
		
		if ("LoadMeta".equals(cmd)) {
			this.loadMeta(request, fcb);
			return;
		}
		
		// TODO check security  ???
		if ("SaveMeta".equals(cmd)) {
			this.saveMeta(request, fcb);
			return;
		}
		
		// TODO combine meta with detail
		if ("ImageDetail".equals(cmd)) {
			this.imageDetail(request, fcb);
			return;
		}
		
		super.executeCustom(request, fcb);
	}
	
	public void imageDetail(RecordStruct request, OperationOutcomeStruct fcb) throws OperatingContextException {
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
				
				if (! fi.exists()) {
					Logger.error("Your request appears valid but does not map to a folder.  Unable to complete.");
					fcb.returnEmpty();
					return;
				}
				
				GalleryVault.this.fsd.getFolderListing(fi.getPathAsCommon(), new OperationOutcome<List<FileStoreFile>>() {
					@Override
					public void callback(List<FileStoreFile> result) throws OperatingContextException {
						if (this.hasErrors() || this.isEmptyResult()) {
							fcb.returnEmpty();
							return;
						}
						
						ListStruct files = new ListStruct();
						
						for (FileStoreFile file : this.getResult()) {
							if (file.getName().startsWith("."))
								continue;
							
							String fname = file.getName();
							
							if (! fname.endsWith(".jpg") && ! fname.endsWith(".jpeg") && ! fname.endsWith(".png")
									&& ! fname.endsWith(".gif"))
								continue;
							
							RecordStruct fdata = new RecordStruct();
							
							String ext = file.getPathAsCommon().getFileExtension();
							
							fdata.with("Alias", fname.substring(0, fname.length() - ext.length() - 1));
							fdata.with("Extension", ext);
							fdata.with("LastModified", file.getModification());
							fdata.with("Size", file.getSize());
							
							files.with(fdata);
						}
						
						GalleryVault.this.fsd.getFileDetail(fi.getPathAsCommon().resolve("meta.json"), new OperationOutcome<FileStoreFile>() {
							@Override
							public void callback(FileStoreFile mfi) throws OperatingContextException {
								if (this.hasErrors() || this.isEmptyResult()) {
									fcb.returnEmpty();
									return;
								}
								
								if (! mfi.exists()) {
									fcb.returnValue(new RecordStruct().with("Extra", RecordStruct.record()
											.with("Files",	files)
											.with("Meta", null)
									));
								}
								else {
									mfi.readAllText(new OperationOutcome<String>() {
										@Override
										public void callback(String meta) throws OperatingContextException {
											fcb.returnValue(new RecordStruct().with("Extra", RecordStruct.record()
													.with("Files", files)
													.with("Meta", Struct.objectToComposite(meta))
											));
										}
									});
								}
							}
						});
					}
				});
			}
		});
	}
	
	public void saveMeta(RecordStruct request, OperationOutcomeStruct fcb) throws OperatingContextException {
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
				
				if (! fi.exists()) {
					Logger.error("Your request appears valid but does not map to a folder.  Unable to complete.");
					fcb.returnEmpty();
					return;
				}
				
				GalleryVault.this.fsd.getFileDetail(fi.getPathAsCommon().resolve("meta.json"), new OperationOutcome<FileStoreFile>() {
					@Override
					public void callback(FileStoreFile result) throws OperatingContextException {
						if (this.hasErrors() || this.isEmptyResult()) {
							fcb.returnEmpty();
							return;
						}
						
						FileStoreFile mfi = this.getResult();
						
						// save part as deposit
						MemoryStoreFile msource = MemoryStoreFile.of(mfi.getPathAsCommon())
								.with(request.getFieldAsRecord("Params").toPrettyString());
						
						VaultUtil.transfer(GalleryVault.this.name, msource, mfi.getPathAsCommon(), null, fcb);

							/*
							mfi.writeAllText(request.getFieldAsRecord("Params").toPrettyString(), new OperationOutcomeEmpty() {
								@Override
								public void callback() {
									fcb.returnEmpty();
								}
							});
							*/
					}
				});
			}
		});
	}
	
	public void loadMeta(RecordStruct request, OperationOutcomeStruct fcb) throws OperatingContextException {
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
				
				if (! fi.exists()) {
					if (request.selectAsBooleanOrFalse("Params.Search")) {

						RecordStruct meta = GalleryUtil.getMeta(CommonPath.from(request.selectAsString("Path")).getParent().toString());

						if (meta != null)
							fcb.returnValue(new RecordStruct()
									.with("Extra", meta)
							);
						else
							fcb.returnEmpty();
					}
					else {
						Logger.error("Your request appears valid but does not map to a folder.  Unable to complete.");
						fcb.returnEmpty();
					}
					
					return;
				}
				
				GalleryVault.this.fsd.getFileDetail(fi.getPathAsCommon().resolve("meta.json"), new OperationOutcome<FileStoreFile>() {
					@Override
					public void callback(FileStoreFile result) throws OperatingContextException {
						if (this.hasErrors() || this.isEmptyResult()) {
							fcb.returnEmpty();
							return;
						}
						
						FileStoreFile mfi = this.getResult();
						
						if (! mfi.exists()) {
							if (fi.getPathAsCommon().isRoot()) {
								fcb.returnEmpty();
								return;
							}
							
							request.with("Path", fi.getPathAsCommon().getParent().toString());
							
							GalleryVault.this.executeCustom(request, fcb);
							return;
						}
						
						mfi.readAllText(new OperationOutcome<String>() {
							@Override
							public void callback(String result) throws OperatingContextException {
								if (this.isNotEmptyResult())
									fcb.returnValue(new RecordStruct()
											.with("Extra", Struct.objectToComposite(this.getResult()))
									);
								else
									fcb.returnEmpty();
							}
						});
					}
				});
			}
		});
	}
}
