package dcraft.filevault;

import dcraft.db.BasicRequestContext;
import dcraft.db.IConnectionManager;
import dcraft.db.fileindex.FileIndexAdapter;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileDescriptor;
import dcraft.filestore.FileStore;
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.local.LocalStore;
import dcraft.filestore.local.LocalStoreFile;
import dcraft.filevault.work.VaultIndexLocalFilesWork;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationMarker;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.hub.op.OperationOutcomeString;
import dcraft.log.Logger;
import dcraft.stream.StreamFragment;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.IWork;
import dcraft.tenant.Site;
import dcraft.util.FileUtil;
import dcraft.util.cb.CountDownCallback;
import dcraft.xml.XElement;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class FileStoreVault extends Vault {
	protected FileStore fsd = null;
	
	@Override
	public void init(Site di, XElement bel, OperationOutcomeEmpty cb) throws OperatingContextException {
		super.init(di, bel, cb);
		
		String root = bel.hasNotEmptyAttribute("DirectPath")
				? bel.getAttribute("DirectPath")
				: di.resolvePath(bel.getAttribute("RootFolder", "./vault/" + this.name)).toAbsolutePath().normalize().toString();

		RecordStruct cparams = new RecordStruct().with("RootFolder", root);
		
		// TODO enhance, someday this doesn't have to be a local FS
		this.fsd = new LocalStore();
		
		this.fsd.connect(cparams, cb);
	}
	
	public FileStore getFileStore() {
		return this.fsd;
	}

	// TODO should use a callback approach
	@Override
	public void beforeSubmitTransaction(TransactionBase tx) throws OperatingContextException {
		FileStore vfs = this.getFileStore();
		
		// TODO delete file will not work when replaying deposits, check first if there is a newer version of file Present
		// would also not work for folders, but that will be solved by listing only file entries

		if (vfs instanceof LocalStore) {
			IConnectionManager connectionManager = ResourceHub.getResources().getDatabases().getDatabase();

			FileIndexAdapter adapter = FileIndexAdapter.of(BasicRequestContext.of(connectionManager.allocateAdapter()));

			long dstemp = tx.getTimestamp().toEpochSecond();

			// delete files from transaction
			for (TransactionFile delete : tx.getDeletelist()) {
				RecordStruct frec = adapter.fileInfo(this, delete.getPath(), OperationContext.getOrThrow());

				if ((frec != null) && ! frec.getFieldAsBooleanOrFalse("IsFolder")) {
					long stamp = frec.getFieldAsInteger("Modified", 0) / 1000;

					// do not delete if there is a newer file in the folder currently
					if (stamp > dstemp)
						continue;
				}

				vfs.fileReference(delete.getPath()).remove(null);        // TODO should wait, doesn't matter with locals though
			}
			
			// cleanup a folder structure if this was a delete folder
			CommonPath cleanup = tx.getCleanFolder();
			
			if (cleanup != null) {
				Path source = ((LocalStore) vfs).resolvePath(cleanup);
				
				List<Path> cleanups = new ArrayList<>();
				
				try {
					if (Files.exists(source)) {
						Files.walkFileTree(source, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
								new SimpleFileVisitor<Path>() {
									@Override
									public FileVisitResult postVisitDirectory(Path sfile, IOException x1) throws IOException {
										if (x1 != null)
											throw x1;
										
										cleanups.add(sfile);
										
										return FileVisitResult.CONTINUE;
									}
								});
					}
					
					for (Path path : cleanups) {
						if (FileUtil.isDirEmpty(path))
							FileUtil.deleteDirectory(path);
					}
				}
				catch (IOException x) {
					Logger.error("Error deleting file tree: " + x);
				}
			}

			// add the tx Update files to the local folder
			for (TransactionFile update : tx.getUpdateList()) {
				RecordStruct frec = adapter.fileInfo(this, update.getPath(), OperationContext.getOrThrow());
				
				if ((frec != null) && ! frec.getFieldAsBooleanOrFalse("IsFolder")) {
					long stamp = frec.getFieldAsInteger("Modified", 0) / 1000;

					// do not replace if there is a newer file in the folder currently
					if (stamp > update.getTimestamp().toEpochSecond())
						continue;
				}

				FileUtil.moveFile(tx.getFolder().resolvePath(update.getPath()), ((LocalStore) vfs).resolvePath(update.getPath()));
			}
		}
		else {
			// TODO add Expand for non-local vaults - probably just to the deposit worker since
			// non-local vaults are not guaranteed to be expanded at end of this call
			Logger.error("Non-local Expand Vaults not yet supported!");
		}
	}

	@Override
	public IWork buildIndexWork() {
		return VaultIndexLocalFilesWork.of(this);
	}
	
	@Override
	public void getFileDetail(CommonPath path, RecordStruct extra, OperationOutcome<FileDescriptor> fcb) throws OperatingContextException {
		this.fsd.getFileDetail(path, new OperationOutcome<FileStoreFile>() {
			@Override
			public void callback(FileStoreFile result) throws OperatingContextException {
				fcb.returnValue(result);
			}
		});
	}
	
	@Override
	public void addFolder(CommonPath path, RecordStruct params, OperationOutcome<FileDescriptor> callback) throws OperatingContextException {
		this.fsd.addFolder(path, new OperationOutcome<FileStoreFile>() {
			@Override
			public void callback(FileStoreFile result) throws OperatingContextException {
				callback.returnValue(result);
			}
		});
	}
	
	@Override
	public void deleteFiles(List<FileDescriptor> files, RecordStruct params, OperationOutcomeEmpty callback) throws OperatingContextException {
		// check if `file` has info loaded
		for (FileDescriptor file : files) {
			if (!file.confirmed()) {
				Logger.error("Get file details first");
				callback.returnEmpty();
				return;
			}
		}
		
		OperationMarker om = OperationMarker.create();
		
		CountDownCallback countDownCallback = new CountDownCallback(files.size(), new OperationOutcomeEmpty() {
			@Override
			public void callback() throws OperatingContextException {
				if (om.hasErrors()) {
					callback.returnEmpty();
					return;
				}
				
				Transaction ftx = buildUpdateTransaction(Transaction.createTransactionId(), params);
				
				for (FileDescriptor file : files) {
					if (! file.exists()) {
						continue;	// skip
					}
					// if this was a folder, list all the files removed for a safer transaction
					// allows us to replay the transaction out of order
					// deleting folders can be delayed before it shows to user as the deletes don't actually occur until TX is run
					else if (file.isFolder()) {
						FileStore vfs = getFileStore();
						
						if (vfs instanceof LocalStore) {
							Path base = ((LocalStore) vfs).getPath();
							Path source = ((LocalStoreFile) file).getLocalPath();
							
							try {
								Files.walkFileTree(source, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
										new SimpleFileVisitor<Path>() {
											@Override
											public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
												if (file.endsWith(".DS_Store"))
													return FileVisitResult.CONTINUE;
												
												Path dest = base.relativize(file);
												
												ftx.withDelete(TransactionFile.of(CommonPath.from("/" + dest.toString()), ftx.getTimestamp()));
												
												return FileVisitResult.CONTINUE;
											}
										});
								
								ftx.withCleanFolder(file.getPathAsCommon());
						
								/*
								ftx.commitTransaction(new OperationOutcomeEmpty() {
									@Override
									public void callback() throws OperatingContextException {
										afterRemove(file, params, callback);
									}
								});
								*/
							}
							catch (IOException x) {
								Logger.error("Error copying file tree: " + x);
								callback.returnEmpty();
							}
						}
						else {
							// TODO add delete folder for non-local vaults
							Logger.error("Non-local Vaults do not yet support folder delete!");
							callback.returnEmpty();
						}
					}
					else {
						ftx.withDelete(TransactionFile.of(file.getPathAsCommon(), ftx.getTimestamp()));
						
						/*
						((FileStoreFile) file).remove(new OperationOutcomeEmpty() {
							@Override
							public void callback() throws OperatingContextException {
								ftx.commitTransaction(new OperationOutcomeEmpty() {
									@Override
									public void callback() throws OperatingContextException {
										afterRemove(file, params, callback);
									}
								});
							}
						});
						*/
					}
				}
				
				ftx.commitTransaction(new OperationOutcomeEmpty() {
					@Override
					public void callback() throws OperatingContextException {
						CountDownCallback countDownCallback2 = new CountDownCallback(files.size(), callback);
						
						for (FileDescriptor file : files) {
							afterRemove(file, params, new OperationOutcomeEmpty() {
								@Override
								public void callback() throws OperatingContextException {
									countDownCallback2.countDown();
								}
							});
						}
					}
				});
			}
		});
		
		for (FileDescriptor file : files) {
			this.beforeRemove(file, params, new OperationOutcomeEmpty() {
				@Override
				public void callback() throws OperatingContextException {
					countDownCallback.countDown();
				}
			});
		}
	}

	@Override
	public void moveFile(FileDescriptor source, FileDescriptor dest, RecordStruct params, OperationOutcomeEmpty callback) throws OperatingContextException {
		// check if `file` has info loaded
		if (! source.confirmed() || ! dest.confirmed()) {
			Logger.error("Get file details first");
			callback.returnEmpty();
			return;
		}

		if (! source.exists()) {
			Logger.error("Folder not valid, unable to rename files");
			callback.returnEmpty();
			return;
		}

		this.beforeMove(source, dest, params, new OperationOutcomeEmpty() {
			@Override
			public void callback() throws OperatingContextException {
				if (this.hasErrors()) {
					callback.returnEmpty();
					return;
				}

				Transaction ftx = buildUpdateTransaction(Transaction.createTransactionId(), params);

				// if this was a folder, list all the files moved for a safer transaction
				// allows us to replay the transaction out of order
				// moving folders can be delayed before it shows to user as the deletes don't actually occur until TX is run
				// this could mess up if a file started uploading after walktree starts, basically move needs to be done when no other operations are occuring to the folder

				if (source.isFolder()) {
					FileStore vfs = getFileStore();

					if (vfs instanceof LocalStore) {
						Path lbase = ((LocalStore) vfs).getPath();
						Path lsource = ((LocalStoreFile) source).getLocalPath();

						try {
							Files.walkFileTree(lsource, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
									new SimpleFileVisitor<Path>() {
										@Override
										public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
											if (file.endsWith(".DS_Store"))
												return FileVisitResult.CONTINUE;

											Path dest = lbase.relativize(file);

											ftx.withDelete(TransactionFile.of(CommonPath.from("/" + dest.toString()), ftx.getTimestamp()));

											return FileVisitResult.CONTINUE;
										}
									});

							ftx.withCleanFolder(source.getPathAsCommon());

							Path ldest = ftx.getFolder().resolvePath(dest.getPathAsCommon());

							FileUtil.moveFolder(lsource, ldest);

							// update list is built automatically from inside commit
							ftx.commitTransaction(new OperationOutcomeEmpty() {
								@Override
								public void callback() throws OperatingContextException {
									afterMove(source, dest, params, callback);
								}
							});
						}
						catch (IOException x) {
							Logger.error("Error move file tree: " + x);
							callback.returnEmpty();
						}
					}
					else {
						// TODO add delete folder for non-local vaults
						Logger.error("Non-local Vaults do not yet support folder move!");
						callback.returnEmpty();
					}
				}
				else {
					FileStore vfs = getFileStore();

					if (vfs instanceof LocalStore) {
						Path lsource = ((LocalStoreFile) source).getLocalPath();

						ftx.withDelete(TransactionFile.of(source.getPathAsCommon(), ftx.getTimestamp()));

						Path ldest = ftx.getFolder().resolvePath(dest.getPathAsCommon());

						FileUtil.moveFile(lsource, ldest);

						// update list is built automatically from inside commit
						ftx.commitTransaction(new OperationOutcomeEmpty() {
							@Override
							public void callback() throws OperatingContextException {
								afterMove(source, dest, params, callback);
							}
						});
					}
					else {
						// TODO add delete folder for non-local vaults
						Logger.error("Non-local Vaults do not yet support file move!");
						callback.returnEmpty();
					}
				}
			}
		});
	}

	@Override
	public StreamFragment toSourceStream(FileDescriptor file) throws OperatingContextException {
		return ((FileStoreFile) file).allocStreamSrc();
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
					
					files.add(file);
				}
				
				callback.returnValue(files);
			}
		});
	}
	
	@Override
	public void hashFile(FileDescriptor file, String evidence, RecordStruct params, OperationOutcomeString callback) throws OperatingContextException {
		((FileStoreFile) file).hash(evidence, callback);
	}
	
	@Override
	public Transaction buildUpdateTransaction(String txid, RecordStruct params) {
		Transaction tx = new Transaction();
		
		tx.id = txid;
		tx.vault = this;
		tx.nodeid = ApplicationHub.getNodeId();
		
		return tx;
	}
	
	/* TODO review - not a terrible idea
	protected void watch(String op, FileStoreFile file) {
		XElement settings = this.getLoader().getSettings();
		
		if (settings != null) {
	        for (XElement watch : settings.selectAll("Watch")) {
	        	String wpath = watch.getAttribute("FilePath");
	        	
	        	// if we are filtering on path make sure the path is a parent of the triggered path
	        	if (StringUtil.isNotEmpty(wpath)) {
	        		CommonPath wp = new CommonPath(wpath);
	        		
	        		if (!wp.isParent(file.path()))
	        			continue;
	        	}
        	
                String tasktag = op + "Task";
                
    			for (XElement task : watch.selectAll(tasktag)) {
    				String id = task.getAttribute("Id");
    				
    				if (StringUtil.isEmpty(id))
    					id = Task.nextTaskId();
    				
    				String title = task.getAttribute("Title");			        				
    				String scriptold = task.getAttribute("Script");
    				String params = task.selectFirstText("Params");
    				RecordStruct prec = null;
    				
    				if (StringUtil.isNotEmpty(params)) {
    					FuncResult<CompositeStruct> pres = CompositeParser.parseJson(params);
    					
    					if (pres.isNotEmptyResult())
    						prec = (RecordStruct) pres.getResult();
    				}
    				
    				if (prec == null) 
    					prec = new RecordStruct();
    				
			        prec.setField("File", file);
    				
    				if (scriptold.startsWith("$"))
    					scriptold = scriptold.substring(1);
    				
    				Task t = new Task()
    					.withId(id)
    					.withTitle(title)
    					.withParams(prec)
    					.withRootContext();
    				
    				if (!ScriptWork.addScript(t, Paths.get(scriptold))) {
    					Logger.error("Unable to run scriptold for file watcher: " + watch.getAttribute("FilePath"));
    					continue;
    				}
    				
    				Hub.instance.getWorkPool().submit(t);
    			}
	        }
		}
	}
		*/
}
