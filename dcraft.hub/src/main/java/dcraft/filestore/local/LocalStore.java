/* ************************************************************************
#
#  designCraft.io
#
#  http://designcraft.io/
#
#  Copyright:
#    Copyright 2014 eTimeline, LLC. All rights reserved.
#
#  License:
#    See the license.txt file in the project's top-level directory for details.
#
#  Authors:
#    * Andy White
#
************************************************************************ */
package dcraft.filestore.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.util.ArrayList;
import java.util.List;

import dcraft.filestore.*;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationMarker;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.log.Logger;
import dcraft.schema.SchemaHub;
import dcraft.script.work.ReturnOption;
import dcraft.script.StackUtil;
import dcraft.script.work.StackWork;
import dcraft.struct.BaseStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.IParentAwareWork;
import dcraft.util.FileUtil;
import dcraft.util.StringUtil;
import dcraft.util.io.IFileWatcher;
import dcraft.xml.XElement;

public class LocalStore extends FileStore {
	static public LocalStore ofServer() {
		LocalStore driver = new LocalStore();
		driver.with("RootFolder", ".");
		driver.connect(null, null);
		return driver;
	}
	
	static public LocalStore of(String path) {
		LocalStore driver = new LocalStore();
		driver.with("RootFolder", path);
		driver.connect(null, null);
		return driver;
	}
	
	static public LocalStore of(Path path) {
		LocalStore driver = new LocalStore();
		driver.with("RootFolder", ".".equalsIgnoreCase(path.toString()) ? "." : path.normalize().toString());
		driver.connect(null, null);
		return driver;
	}
	
	protected Path localpath = null;
	protected List<IFileWatcher> observers = new ArrayList<>();
	
	protected boolean tempfolder = false;
	
	public LocalStore() {
		this.withType(SchemaHub.getType("dcLocalStore"));
	}
	
	public Path getPath() {
		return this.localpath;
	}
	
	protected void setPath(String v) {
		this.localpath = Paths.get(v).normalize().toAbsolutePath();
		
		if (Files.exists(this.localpath) && ! Files.isDirectory(this.localpath)) {
			Logger.error("File Store cannot be mounted: " + v);
			return;
		}
		
		try {
			Files.createDirectories(this.localpath);
			
			this.with("RootFolder", this.localpath.toString().replace('\\', '/'));
		}
		catch (Exception x) {
			Logger.errorTr(132, x);
		}
	}
	
	public Path resolvePath(String path) {
		return this.localpath.resolve(path.startsWith("/") ? path.substring(1) : path).normalize().toAbsolutePath();
	}
	
	public Path resolvePath(Path path) {
		if (path.isAbsolute()) {
			if (path.startsWith(this.localpath))
				return path;
			
			return null;
		}
		
		return this.localpath.resolve(path).normalize().toAbsolutePath();
	}
	
	public Path resolvePath(CommonPath path) {
		return this.localpath.resolve(path.toString().substring(1)).normalize().toAbsolutePath();
	}
	
	public LocalStoreFile resolvePathToStore(String path) {
		return LocalStoreFile.of(this, this.resolvePath(path));
	}
	
	public String relativize(Path path) {
		path = path.normalize().toAbsolutePath();
		
		if (path == null)
			return null;
		
		String rpath = path.toString().replace('\\', '/');
		
		if (! rpath.startsWith(this.getFieldAsString("RootFolder")))
			return null;
		
		return rpath.substring(this.getFieldAsString("RootFolder").length());
	}
	
	public void withObserver(IFileWatcher v) {
		this.observers.add(v);
	}
	
	public void fireFolderEvent(Path fname, WatchEvent.Kind<Path> kind) {
		for (IFileWatcher watcher : this.observers)
			watcher.fireFolderEvent(fname, kind);
	}
	
	@Override
	public LocalStore deepCopy() {
		LocalStore cp = new LocalStore();
		this.doCopy(cp);
		return cp;
	}

	@Override
	protected void doCopy(BaseStruct n) {
		LocalStore cp = (LocalStore) n;
		cp.localpath = this.localpath;

		super.doCopy(n);
	}

	@Override
	public boolean close(OperationOutcomeEmpty callback) {
		if (this.isTemp())
			FileUtil.deleteDirectory(this.getPath());
		
		return true;
	}
	
	@Override
	public void connect(RecordStruct params, OperationOutcomeEmpty callback) {
		if ((params != null) && ! params.isFieldEmpty("RootFolder")) 
			this.setRootFolder(params.getFieldAsString("RootFolder"));
		
		// create root folder if we have one specified and it is not present
		if (! this.isFieldEmpty("RootFolder"))
			this.setPath(this.getFieldAsString("RootFolder"));

		if (callback != null)
			callback.returnResult();
	}

	@Override
	public ReturnOption operation(StackWork stack, XElement codeEl) throws OperatingContextException {
		// connect and close in super
		
		if ("AllocateTempDir".equals(codeEl.getName())) {
			Path tfolder = FileUtil.allocateTempFolder();
			
			try {
				this.with("RootFolder", tfolder.toFile().getCanonicalPath());
			} 
			catch (IOException x) {
				// TODO Auto-generated catch block
			}
			
			return ReturnOption.CONTINUE;
		}
		
		if ("Delete".equals(codeEl.getName())) {
			FileUtil.deleteDirectory(Paths.get(this.getFieldAsString("RootFolder")));
			
			return ReturnOption.CONTINUE;
		}
		
		if ("MakeDir".equals(codeEl.getName())) {
			FileUtil.confirmOrCreateDir(Paths.get(this.getFieldAsString("RootFolder")));
			return ReturnOption.CONTINUE;
		}
		
		if ("GetInfo".equals(codeEl.getName())) {
			String path = StackUtil.stringFromElement(stack, codeEl, "Path");

			if (StringUtil.isEmpty(path)) {
				Logger.error("Missing path fpr GetInfo");
				return ReturnOption.CONTINUE;
			}
			
			boolean absolute = StackUtil.boolFromElement(stack, codeEl, "Absolute", false);
			
	        String handle = StackUtil.stringFromElement(stack, codeEl, "Handle");

			if (handle != null) 
				if (absolute)
					StackUtil.addVariable(stack, handle,
							LocalStoreFile.of(LocalStore.this, Paths.get(path)));
				else
					StackUtil.addVariable(stack, handle,
							LocalStoreFile.of(LocalStore.this, RecordStruct.record().with("Path", path)));
			
			return ReturnOption.CONTINUE;
		}
		
		/*
		if ("Put".equals(codeEl.getName())) {
			
			// TODO integrate with put method below
			
			Struct src = stack.refFromElement(codeEl, "Source");

			if (!(src instanceof IFileStoreFile) && ! (src instanceof RecordStruct)) {
				// TODO log wrong type
				stack.resume();
				return;
			}
			
			boolean relative = stack.boolFromElement(codeEl, "Relative", true);
			
			this.put((IFileStoreFile)src, relative, new OperationOutcome<IFileStoreFile>() {				
				@Override
				public void callback() {
					// TODO check errors
					
			        String handle = stack.stringFromElement(codeEl, "Handle");

					if (handle != null) 
			            stack.addVariable(handle, (Struct) this.getResult());
					
					stack.resume();
				}
			});
			
			return;
		}
		
		
		if ("PutAll".equals(codeEl.getName())) {
			
			// TODO integrate with put method below
			
			Struct src = stack.refFromElement(codeEl, "Source");

			if (src == null) {
				// TODO log missing
				stack.resume();
				return;
			}

			if (!(src instanceof IItemCollection)) {
				// TODO log wrong type
				stack.resume();
				return;
			}
			
			boolean relative = stack.boolFromElement(codeEl, "Relative", true);
			
			this.putAll((IItemCollection)src, relative, new OperationNoOutcome() {				
				@Override
				public void callback() {
					// TODO check errors
					System.out.println("done");
					
					stack.resume();
				}
			});
			
			return;
		}
		*/
		
		/*
		if ("ChangeDirectory".equals(code.getName())) {
			String path = stack.stringFromElement(code, "Path");
			
			if (StringUtil.isEmpty(path)) {
				// TODO log
				stack.resume();
				return;
			}
			
			this.cwd = new File(path);
			
			stack.resume();
			return;
		}
		
		if ("ScanFilter".equals(code.getName())) {
			String path = stack.stringFromElement(code, "Path");
			
			...
			
			if (StringUtil.isEmpty(path)) {
				// TODO log
				stack.resume();
				return;
			}
			
			this.cwd = new File(path);
			
			stack.resume();
			return;
		}
		*/
		
		//System.out.println("fs operation: " + code);
		
		return super.operation(stack, codeEl);
	}

	public FileStoreFile wrapFileRecord(RecordStruct file) {
		return LocalStoreFile.of(this, file);
	}

	@Override
	public void getFileDetail(CommonPath path, OperationOutcome<FileStoreFile> callback) {
		LocalStoreFile f = LocalStoreFile.of(this, path);
		
		callback.returnValue(f);
	}

	public String getRootFolder() {
		return this.getFieldAsString("RootFolder");
	}

	public void setRootFolder(String path) {
		this.with("RootFolder", path);
	}

	@Override
	public void addFolder(CommonPath path, OperationOutcome<FileStoreFile> callback) {
		OperationMarker ok = OperationMarker.create();
		
		Path localpath = this.resolvePath(path);
		
		if (Files.exists(localpath)) {
			if (!Files.isDirectory(localpath)) 
				Logger.error("Path is not a folder: " + localpath);
		}
		else {
			FileUtil.confirmOrCreateDir(localpath);
		}
		
		try {
			if (! ok.hasErrors()) {
				callback.returnValue(LocalStoreFile.of(this, path));
				return;
			}
		}
		catch (OperatingContextException x) {
			Logger.error("Bad context after add: " + x);
		}
		
		callback.returnValue(null);
	}
	
	@Override
	public void removeFile(CommonPath path, OperationOutcomeEmpty callback) {
		Path localpath = this.resolvePath(path);

		if (Files.isDirectory(localpath)) {
			FileUtil.deleteDirectory(localpath);
		}
		else {
			try {
				Files.deleteIfExists(localpath);
			}
			catch (IOException x) {
				Logger.warn("Unable to remove file: " + x);
			}
		}
		
		callback.returnEmpty();
	}

	@Override
	public void queryFeatures(OperationOutcome<RecordStruct> callback) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void customCommand(RecordStruct params, OperationOutcome<RecordStruct> callback) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public IFileCollection scanner(CommonPath path) {
		return LocalStoreFile.of(this, path).scanner();
	}

	@Override
	public LocalStoreFile rootFolder() {
		return LocalStoreFile.of(this, CommonPath.ROOT);
	}
	
	@Override
	public LocalStoreFile fileReference(CommonPath path) {
		return LocalStoreFile.of(this, path);
	}
	
	@Override
	public LocalStoreFile fileReference(CommonPath path, boolean isFolder) {
		return LocalStoreFile.of(this, path, isFolder);
	}
	
	@Override
	public void getFolderListing(CommonPath path, OperationOutcome<List<FileStoreFile>> callback) {
		LocalStoreFile f = LocalStoreFile.of(this, path);
		
		f.getFolderListing(callback);
	}
	
	public void isTemp(boolean v) {
		this.tempfolder = v;
	}
	
	public boolean isTemp() {
		return this.tempfolder;
	}
	
	/*
	// return true if got lock
	@Override
	public boolean tryLocalLock(CommonPath path) {
		this.locallockslock.lock();

		try {
			if (!this.locallocks.contains(path)) {
				this.locallocks.add(path);
				System.out.println("File locked: " + path);
				return true;
			}
		}
		finally {
			this.locallockslock.unlock();
		}
		
		System.out.println("Failed file locked: " + path);
		
		return false;
	}

	@Override
	public void releaseLocalLock(CommonPath path) {
		this.locallockslock.lock();

		try {
			if (this.locallocks.remove(path))
				System.out.println("File unlocked: " + path);
			else
				System.out.println("Bad file unlock: " + path);
		}
		finally {
			this.locallockslock.unlock();
		}
	}
	*/
}
