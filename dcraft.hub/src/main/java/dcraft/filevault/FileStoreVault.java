package dcraft.filevault;

import dcraft.filestore.CommonPath;
import dcraft.filestore.FileDescriptor;
import dcraft.filestore.FileStore;
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.local.LocalStore;
import dcraft.filevault.work.VaultIndexLocalFilesWork;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.hub.op.OperationOutcomeString;
import dcraft.log.Logger;
import dcraft.stream.StreamFragment;
import dcraft.struct.RecordStruct;
import dcraft.task.IWork;
import dcraft.tenant.Site;
import dcraft.util.FileUtil;
import dcraft.xml.XElement;

import java.util.ArrayList;
import java.util.List;

public class FileStoreVault extends Vault {
	protected FileStore fsd = null;
	
	@Override
	public void init(Site di, XElement bel, OperationOutcomeEmpty cb) {
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
			// delete and move
			for (CommonPath delete : tx.getDeletelist())
				vfs.fileReference(delete).remove(null);		// TODO should wait, doesn't matter with locals though
			
			FileUtil.moveFileTree(tx.getFolder().getPath(), ((LocalStore) vfs).getPath(), null);
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
	public void deleteFile(FileDescriptor file, RecordStruct params, OperationOutcomeEmpty callback) throws OperatingContextException {
		// check if `file` has info loaded
		if (! file.confirmed()) {
			Logger.error("Get file details first");
			callback.returnEmpty();
			return;
		}
		
		this.beforeRemove(file, params, new OperationOutcomeEmpty() {
			@Override
			public void callback() throws OperatingContextException {
				if (this.hasErrors() || ! file.exists()) {
					callback.returnEmpty();
					return;
				}
				
				((FileStoreFile) file).remove(new OperationOutcomeEmpty() {
					@Override
					public void callback() throws OperatingContextException {
						Transaction ftx = buildUpdateTransaction(Transaction.createTransactionId(), params);
						
						// TODO if this was a folder, instead list all the files removed for a safer transaction
						// allows us to replay the transaction out of order
						ftx.withDelete(file.getPathAsCommon());
						
						ftx.commitTransaction(new OperationOutcomeEmpty() {
							@Override
							public void callback() throws OperatingContextException {
								afterRemove(file, params, callback);
							}
						});
					}
				});
			}
		});
	}
	
	@Override
	public StreamFragment toSourceStream(FileDescriptor file) throws OperatingContextException {
		return StreamFragment.of(((FileStoreFile) file).allocStreamSrc());
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
