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
package dcraft.filevault;

import dcraft.filestore.*;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.log.Logger;
import dcraft.schema.SchemaHub;
import dcraft.script.StackUtil;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.stream.StreamFragment;
import dcraft.stream.StreamWork;
import dcraft.stream.file.IFileStreamDest;
import dcraft.stream.file.MemoryDestStream;
import dcraft.struct.RecordStruct;
import dcraft.struct.ScalarStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.BinaryStruct;
import dcraft.struct.scalar.NullStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.task.TaskContext;
import dcraft.task.TaskHub;
import dcraft.task.TaskObserver;
import dcraft.util.*;
import dcraft.xml.XElement;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class EncryptedVaultFile extends FileStoreFile {
	static public EncryptedVaultFile of(EncryptedVaultStore driver, CommonPath path) throws OperatingContextException {
		EncryptedVaultFile file = new EncryptedVaultFile();

		file.driver = driver;
		file.localdriver = driver;

		file.withPath(path);

		file.refreshProps();

		return file;
	}

	static public EncryptedVaultFile ofFileIndex(EncryptedVaultStore driver, CommonPath path, RecordStruct index) throws OperatingContextException {
		EncryptedVaultFile file = new EncryptedVaultFile();

		file.driver = driver;
		file.localdriver = driver;

		file.withPath(path);

		file.withIndexInfo(index);

		return file;
	}

	static public EncryptedVaultFile reference(EncryptedVaultStore driver, CommonPath path, boolean isfolder) {
		EncryptedVaultFile file = new EncryptedVaultFile();

		file.driver = driver;
		file.localdriver = driver;

		file
				.withPath(path)
				.withIsFolder(isfolder);

		return file;
	}

	protected EncryptedVaultStore localdriver = null;

	public EncryptedVaultFile() {
		this.withType(SchemaHub.getType("dcEncryptedVaultFile"));
	}
	
    public void refreshProps() throws OperatingContextException {
		this.driver.getFileDetail(this.getPathAsCommon(), new OperationOutcome<FileStoreFile>() {
			@Override
			public void callback(FileStoreFile result) throws OperatingContextException {
				EncryptedVaultFile.this
						.withExists(result.exists())
						.withConfirmed(result.confirmed())
						.withIsFolder(result.isFolder())
						.withPath(result.getPath())
						.withModificationTime(result.getModificationAsTime());
			}
		});
    }

    public void withIndexInfo(RecordStruct info) {
		this
				.withExists("Present".equals(info.getFieldAsString("State")))
				.withConfirmed(true)
				.withIsFolder(info.getFieldAsBooleanOrFalse("IsFolder"));

		if (info.isNotFieldEmpty("Modified")) {
			BigDecimal epoch = info.getFieldAsDecimal("Modified");

			this.withModificationTime(ZonedDateTime.ofInstant(Instant.ofEpochMilli(epoch.longValue()), ZoneId.of("UTC")));
		}
	}

	public String getFullPath() {
		return this.getFieldAsString("FullPath");
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
	public IFileCollection scanner() {
		//if (this.isFolder())
		//	return LocalStoreScanner.of(this.getDriver(), this.getPathAsCommon());
		
		return null;
	}
	
	@Override
	public void getFolderListing(OperationOutcome<List<FileStoreFile>> callback) throws OperatingContextException {
		if (! this.exists() || ! this.isFolder()) {
			Logger.error("Requested path is not a folder");
			callback.returnValue(null);
			return;
		}

		this.localdriver.vault.getFolderListing(this, null, new OperationOutcome<List<? extends FileDescriptor>>() {
			@Override
			public void callback(List<? extends FileDescriptor> result) throws OperatingContextException {
				callback.returnValue((List<FileStoreFile>) result);
			}
		});
	}
	
	@Override
	public StreamFragment allocStreamDest() {
		return null;		//  not currently supported
	}

	@Override
	public StreamFragment allocStreamSrc() throws OperatingContextException {
		return this.localdriver.vault.toSourceStream(this);
	}

	@Override
    protected void doCopy(Struct n) {
    	super.doCopy(n);
    	
    	EncryptedVaultFile nn = (EncryptedVaultFile)n;
		nn.driver = this.driver;
		nn.localdriver = this.localdriver;
    }
    
	@Override
	public EncryptedVaultFile deepCopy() {
		EncryptedVaultFile cp = new EncryptedVaultFile();
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
		/*
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
		*/

		// this is kind of a hack - may want to re-evaluate this later
		// used by NCC provisioning
		if ("ReadText".equals(code.getName())) {
			if (this.exists()) {
		        Struct var = StackUtil.refFromElement(stack, code, "Target", true);
	
		        //System.out.println("e: " + var);
		        
				if ((var == null) || (var instanceof NullStruct)) {
					readAllText(new OperationOutcome<String>() {
						@Override
						public void callback(String result) throws OperatingContextException {
							String handle = StackUtil.stringFromElement(stack, code, "Result");

							if (handle != null)
								StackUtil.addVariable(stack, handle, StringStruct.of(result));

							stack.setState(ExecuteState.DONE);
							OperationContext.getAsTaskOrThrow().resume();
						}
					});

					return ReturnOption.AWAIT;
				}
				else if (var instanceof ScalarStruct) {
					readAllText(new OperationOutcome<String>() {
						@Override
						public void callback(String result) throws OperatingContextException {
							((ScalarStruct)var).adaptValue(result);

							stack.setState(ExecuteState.RESUME);
							OperationContext.getAsTaskOrThrow().resume();
						}
					});

					return ReturnOption.AWAIT;
				}
				else {
					Logger.error("Unable to ReadText, bad target.");
				}
			}
			
			return ReturnOption.CONTINUE;
		}

		if ("ReadBinary".equals(code.getName())) {
			if (this.exists()) {
		        Struct var = StackUtil.refFromElement(stack, code, "Target", true);

		        //System.out.println("e: " + var);

				if ((var == null) || (var instanceof NullStruct)) {
					readAllBinary(new OperationOutcome<Memory>() {
						@Override
						public void callback(Memory result) throws OperatingContextException {
							String handle = StackUtil.stringFromElement(stack, code, "Result");

							if (handle != null)
								StackUtil.addVariable(stack, handle, BinaryStruct.of(result));

							stack.setState(ExecuteState.DONE);
							OperationContext.getAsTaskOrThrow().resume();
						}
					});

					return ReturnOption.AWAIT;
				}
				else if (var instanceof BinaryStruct) {
					readAllBinary(new OperationOutcome<Memory>() {
						@Override
						public void callback(Memory result) throws OperatingContextException {
							((BinaryStruct)var).adaptValue(result);

							stack.setState(ExecuteState.RESUME);
							OperationContext.getAsTaskOrThrow().resume();
						}
					});

					return ReturnOption.AWAIT;
				}
				else {
					Logger.error("Unable to ReadText, bad target.");
				}
			}

			return ReturnOption.CONTINUE;
		}

		/*
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
		*/
		
		return super.operation(stack, code);
	}

	@Override
	public void readAllText(OperationOutcome<String> callback) throws OperatingContextException {
		StreamFragment streamFragment = this.localdriver.vault.toSourceStream(this);
		MemoryDestStream dest = new MemoryDestStream();

		streamFragment.withAppend(dest);

		TaskHub.submit(StreamWork.of(streamFragment), new TaskObserver() {
			@Override
			public void callback(TaskContext task) {
				callback.returnValue(dest.getResultReset().toString());
			}
		});
	}
	
	@Override
	public void writeAllText(String v, OperationOutcomeEmpty callback) {
		Logger.error("write not supported");
		callback.returnResult();
	}
	
	@Override
	public void readAllBinary(OperationOutcome<Memory> callback) throws OperatingContextException {
		StreamFragment streamFragment = this.localdriver.vault.toSourceStream(this);
		MemoryDestStream dest = new MemoryDestStream();

		streamFragment.withAppend(dest);

		TaskHub.submit(StreamWork.of(streamFragment), new TaskObserver() {
			@Override
			public void callback(TaskContext task) {
				callback.returnValue(dest.getResultReset());
			}
		});
	}
	
	@Override
	public void writeAllBinary(Memory v, OperationOutcomeEmpty callback) {
		Logger.error("write not supported");
		callback.returnResult();
	}

	@Override
	public void hash(String method, OperationOutcome<String> callback) {
		Logger.error("hash not supported");

		callback.returnEmpty();
	}

	@Override
	public void rename(String name, OperationOutcomeEmpty callback) {
		// TODO fix this
		Logger.error("rename not supported yet");
		callback.returnResult();
	}

	@Override
	public void remove(OperationOutcomeEmpty callback) throws OperatingContextException {
		if (this.exists()) {
			List<FileDescriptor> files = new ArrayList<>();
			
			files.add(this);
			
			this.localdriver.vault.deleteFiles(files, null, callback);

			this.withExists(false);
		}
		
		if (callback != null)
			callback.returnResult();
	}

}
