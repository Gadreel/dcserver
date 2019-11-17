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
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import dcraft.filestore.*;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.log.Logger;
import dcraft.schema.SchemaHub;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.ReturnOption;
import dcraft.script.StackUtil;
import dcraft.script.work.StackWork;
import dcraft.stream.IStreamSource;
import dcraft.stream.StreamFragment;
import dcraft.stream.file.IFileStreamDest;
import dcraft.struct.RecordStruct;
import dcraft.struct.ScalarStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.NullStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.task.IParentAwareWork;
import dcraft.util.FileUtil;
import dcraft.util.HashUtil;
import dcraft.util.IOUtil;
import dcraft.util.Memory;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class LocalStoreFile extends FileStoreFile {
	static public LocalStoreFile of(LocalStore driver, Path path) {
		LocalStoreFile file = new LocalStoreFile();
		
		file.driver = driver;
		file.localpath = path.normalize().toAbsolutePath();
		file.localdriver = driver;
		
		file.refreshProps();
		
		return file;
	}
	
	static public LocalStoreFile of(LocalStore driver, CommonPath path) {
		LocalStoreFile file = new LocalStoreFile();
		
		file.driver = driver;
		file.localpath = driver.resolvePath(path);
		file.localdriver = driver;
		
		file.refreshProps();
		
		return file;
	}
	
	static public LocalStoreFile of(LocalStore driver, CommonPath path, boolean folder) {
		LocalStoreFile file = new LocalStoreFile();
		
		file.driver = driver;
		file.localpath = driver.resolvePath(path);
		file.localdriver = driver;
		
		file.with("IsFolder", folder);
		
		file.refreshProps();
		
		return file;
	}
	
	static public LocalStoreFile of(LocalStore driver, RecordStruct rec) {
		LocalStoreFile file = new LocalStoreFile();
		
		file.driver = driver;
		file.localdriver = driver;
		
		file.copyFields(rec);
		
		// only works with relative paths - even if my path is / it is considered relative to root
		// which is good
		String cwd = driver.getFieldAsString("RootFolder");
		file.localpath = Paths.get(cwd, rec.getFieldAsString("Path"));
		
		file.refreshProps();
		
		return file;
	}
	
	protected LocalStore localdriver = null;
	protected Path localpath = null;
		
	public LocalStoreFile() {
		this.withType(SchemaHub.getType("dcLocalStoreFile"));
	}
	
    public void refreshProps() {
		// ignore what the caller told us, these are the right values:
		// TODO don't think we need this - this.with("Name", this.localpath.getFileName().toString());
		
		String cwd = this.driver.getFieldAsString("RootFolder");
		//String fpath = this.localpath.normalize().toAbsolutePath().toString();
		String fpath = this.localpath.toString();
		
		// TODO common path format in "absolute" relative to mount review "else" below
		// TODO also, since fpath may be absolute - only do substring thing if cwd is above fpath in folder chain
		
		if (fpath.length() == cwd.length())
			this.with("Path", "/");
		else
			this.with("Path", "/" + fpath.substring(cwd.length() + 1).replace('\\', '/'));
		
		// TODO don't think we need this - this.with("FullPath", fpath);

		
		if (Files.exists(this.localpath)) {
			try {

				//System.out.println("UnFormatted: " + Files.getLastModifiedTime(this.localpath).toMillis());
				//System.out.println("Formatted: " + TimeUtil.stampFmt.print(Files.getLastModifiedTime(this.localpath).toMillis()));
				
				
				this.with("Size", Files.size(this.localpath));
				this.with("Modified", Files.getLastModifiedTime(this.localpath).toInstant());
			} 
			catch (IOException x) {
			}
			
			this.with("IsFolder", Files.isDirectory(this.localpath));
			this.with("Exists", true);
		}
		else
			this.with("Exists", false);
		
		this.withConfirmed(true);
    }

	public Path getLocalPath() {
		return this.localpath;
	}
	
	@Override
	public LocalStore getDriver() {
		return this.localdriver;
	}
	
	@Override
	public RecordStruct getExtra() {
		return null;  // not secure - new RecordStruct().with("FullPath", this.getFullPath());
	}
	
	@Override
	public void getAttribute(String name, OperationOutcome<Struct> callback) {
		// TODO fix this
		Logger.error("attrs not supported yet");
		callback.returnResult();
	}
	
	@Override
	public LocalStoreScanner scanner() {
		if (this.isFolder())
			return LocalStoreScanner.of(this.getDriver(), this.getPathAsCommon());
		
		return null;
	}
	
	@Override
	public void getFolderListing(OperationOutcome<List<FileStoreFile>> callback) {
		Path folder = this.getLocalPath();
		
		if (folder == null) {
			Logger.error("Requested path is invalid");
			callback.returnValue(null);
			return;
		}
		
		if (!Files.exists(folder)) {
			Logger.error("Requested path does not exist");
			callback.returnValue(null);
			return;
		}
		
		if (!Files.isDirectory(folder)) {
			Logger.error("Requested path is not a folder");
			callback.returnValue(null);
			return;
		}
		
		List<FileStoreFile> files = new ArrayList<>();
		
		try (Stream<Path> strm = Files.list(folder)) {
			strm.forEach(entry -> {
				LocalStoreFile f = LocalStoreFile.of(LocalStoreFile.this.getDriver(), entry);
				files.add(f);
			});
			
			callback.returnValue(files);
		}
		catch (IOException x) {
			Logger.error("Problem listing files: " + x);
			
			callback.returnValue(null);
		}
	}
	
	@Override
	public StreamFragment allocStreamDest() {
		return StreamFragment.of(LocalDestStream.from(this));
	}

	@Override
	public StreamFragment allocStreamSrc() {
    	if (this.isFolder()) 
    		return StreamFragment.of(CollectionSourceStream.of(this.scanner()));
    	
		return StreamFragment.of(LocalSourceStream.of(this));
	}

	@Override
    protected void doCopy(Struct n) {
    	super.doCopy(n);
    	
    	LocalStoreFile nn = (LocalStoreFile)n;
		nn.driver = this.driver;
    }
    
	@Override
	public LocalStoreFile deepCopy() {
		LocalStoreFile cp = new LocalStoreFile();
		this.doCopy(cp);
		return cp;
	}

	@Override
	public Struct getOrAllocateField(String name) {
		// TODO consider this
		//if ("TextReader".equals(name))
		//	return new FileSystemTextReader(this);
		
		return super.getOrAllocateField(name);
	}
	
	@Override
	public ReturnOption operation(StackWork stack, XElement code) throws OperatingContextException {
		if ("Hash".equals(code.getName())) {
			String meth = StackUtil.stringFromElement(stack, code, "Method");
			
	        Struct var = StackUtil.refFromElement(stack, code, "Target", true);

			if (var instanceof ScalarStruct) { 				
				try {
					this.hash(meth, new OperationOutcome<String>() {	
						@Override
						public void callback(String result) throws OperatingContextException {
							((ScalarStruct)var).adaptValue(result);
							
							stack.setState(ExecuteState.DONE);
							
							OperationContext.getAsTaskOrThrow().resume();
						}
					});
					
					return ReturnOption.AWAIT;
				} 
				catch (OperatingContextException x) {
					Logger.error("Context error in Hash: " + x);
				}
			}
			else {
				Logger.error("Invalid hash target!");
			}
			
			return ReturnOption.CONTINUE;
		}
		
		if ("Rename".equals(code.getName())) {
			String val = StackUtil.stringFromElement(stack, code, "Value");
			
			if (StringUtil.isEmpty(val)) {
				Logger.error("Unable to rename, missing new name.");
				return ReturnOption.CONTINUE;
			}
			
			Path dest = this.localpath.getParent().resolve(val);
			
			try {
				Files.move(this.localpath, dest, StandardCopyOption.REPLACE_EXISTING);
				
				// TODO this.driver.fireFolderEvent(this.localpath, StandardWatchEventKinds.ENTRY_MODIFY);
				
				this.localpath = dest;
				this.refreshProps();
			} 
			catch (IOException x) {
				Logger.error("Unable to rename file: + x");
			}
			
			return ReturnOption.CONTINUE;
		}

		// this is kind of a hack - may want to re-evaluate this later
		// used by NCC provisioning
		if ("WriteText".equals(code.getName())) {
			String text = code.getText();
			
	        Struct content = StringUtil.isNotEmpty(text) 
	        		? StackUtil.resolveReference(stack, text, true)
	        		: StackUtil.refFromElement(stack, code, "Target", true);
	        
	        if (content != null) {
	        	IOUtil.saveEntireFile(this.localpath, Struct.objectToString(content));
	        	this.refreshProps();
		
				this.localdriver.fireFolderEvent(this.localpath, StandardWatchEventKinds.ENTRY_MODIFY);
	        }
			
			return ReturnOption.CONTINUE;
		}

		// this is kind of a hack - may want to re-evaluate this later
		// used by NCC provisioning
		if ("ReadText".equals(code.getName())) {
			if (this.getFieldAsBooleanOrFalse("Exists")) {
		        Struct var = StackUtil.refFromElement(stack, code, "Target", true);
	
		        //System.out.println("e: " + var);
		        
				if ((var == null) || (var instanceof NullStruct)) {
			        String handle = StackUtil.stringFromElement(stack, code, "Result");

					if (handle != null) 
			            StackUtil.addVariable(stack, handle, StringStruct.of(IOUtil.readEntireFile(this.localpath)));
				}
				else if (var instanceof ScalarStruct) {					
					((ScalarStruct)var).adaptValue(IOUtil.readEntireFile(this.localpath));
				}
				else {
					Logger.error("Unable to ReadText, bad target.");
				}
			}
			
			return ReturnOption.CONTINUE;
		}

		if ("Delete".equals(code.getName())) {
			try {
				if (this.isFolder())
					FileUtil.deleteDirectory(this.localpath);
				else
					Files.deleteIfExists(this.localpath);
				
				this.localdriver.fireFolderEvent(this.localpath, StandardWatchEventKinds.ENTRY_DELETE);
			}
			catch (IOException x) {
				Logger.error("Unable to delete folder: " + x);
			}
			
	    	this.refreshProps();
	    	
			return ReturnOption.CONTINUE;
		}
		
		return super.operation(stack, code);
	}
		
	@Override
	public void readAllText(OperationOutcome<String> callback) {
		CharSequence txtres = IOUtil.readEntireFile(this.localpath);
		
		callback.returnValue((txtres != null) ? txtres.toString() : null);
	}
	
	@Override
	public void writeAllText(String v, OperationOutcomeEmpty callback) {
		IOUtil.saveEntireFile(this.localpath, v);
		
		this.localdriver.fireFolderEvent(this.localpath, StandardWatchEventKinds.ENTRY_MODIFY);
		
		callback.returnResult();
	}
	
	@Override
	public void readAllBinary(OperationOutcome<Memory> callback) {
		callback.returnValue(IOUtil.readEntireFileToMemory(this.localpath));
	}
	
	@Override
	public void writeAllBinary(Memory v, OperationOutcomeEmpty callback) {
		IOUtil.saveEntireFile(this.localpath, v);
		
		this.localdriver.fireFolderEvent(this.localpath, StandardWatchEventKinds.ENTRY_MODIFY);
		
		callback.returnResult();
	}

	@Override
	public void hash(String method, OperationOutcome<String> callback) {
		try {
			String res = HashUtil.hash(method, Files.newInputStream(this.localpath));
			
			callback.returnValue(res);
		}
		catch (Exception x) {
			Logger.error("Unable to read file for hash: " + x);
			
			callback.returnValue(null);
		}
	}

	@Override
	public void rename(String name, OperationOutcomeEmpty callback) {
		// TODO fix this
		Logger.error("rename not supported yet");
		callback.returnResult();
	}

	@Override
	public void remove(OperationOutcomeEmpty callback) {
		if (this.exists()) {
			if (this.isFolder()) {
				FileUtil.deleteDirectory(this.localpath);
			}
			else  {
				try {
					Files.delete(this.localpath);
					
					this.localdriver.fireFolderEvent(this.localpath, StandardWatchEventKinds.ENTRY_DELETE);
				}
				catch (Exception x) {
					Logger.error("Unable to remove file: " + this.getPath() + " - Error: " + x);
				}
			}
		}
		
		if (callback != null)
			callback.returnResult();
	}

}
