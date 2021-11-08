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
package dcraft.filestore.mem;

import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.IFileCollection;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.log.Logger;
import dcraft.schema.SchemaHub;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.stream.IStreamSource;
import dcraft.stream.StreamFragment;
import dcraft.stream.file.IFileStreamDest;
import dcraft.stream.file.MemorySourceStream;
import dcraft.struct.BaseStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.IParentAwareWork;
import dcraft.util.*;
import dcraft.util.io.InputWrapper;
import dcraft.xml.XElement;

import java.util.List;

public class MemoryStoreFile extends FileStoreFile {
	static public MemoryStoreFile of(CommonPath path) {
		MemoryStoreFile file = new MemoryStoreFile();

		file.driver = MemoryStore.store();
		file.localpath = path;

		file.with("IsFolder", false);

		file.refreshProps();

		return file;
	}

	static public MemoryStoreFile of(MemoryStore driver, CommonPath path, boolean folder) {
		MemoryStoreFile file = new MemoryStoreFile();

		file.driver = driver;
		file.localpath = path;

		file.withIsFolder(folder);

		file.refreshProps();

		return file;
	}

	protected CommonPath localpath = null;
	protected Memory binary = null;

	public void setBinary(Memory v) {
		this.with("Size", v.getLength());
		this.binary = v;
	}

	public Memory getBinary() {
		return this.binary;
	}

	public MemoryStoreFile with(Memory v) {
		this.setBinary(v);
		return this;
	}

	public MemoryStoreFile with(String v) {
		this.setBinary(new Memory(v));
		return this;
	}

	public MemoryStoreFile() {
		this.withType(SchemaHub.getType("dcLocalStoreFile"));
	}
	
    public void refreshProps() {
		this.withPath(this.localpath);
		this.with("Modified", TimeUtil.now());
		this.with("Exists", true);
		this.withConfirmed(true);
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
	public void getFolderListing(OperationOutcome<List<FileStoreFile>> callback) {
		Logger.error("Requested path is not a folder");
		callback.returnValue(null);
	}
	
	@Override
	public StreamFragment allocStreamDest() {
		//TODO return MemoryDestDestStream.from(this).withRelative(relative);
		return null;
	}

	@Override
	public StreamFragment allocStreamSrc() {
		return StreamFragment.of(MemorySourceStream.fromBinary(this.binary));
	}

	@Override
    protected void doCopy(BaseStruct n) {
    	super.doCopy(n);
    	
    	MemoryStoreFile nn = (MemoryStoreFile)n;
		nn.driver = this.driver;
    }
    
	@Override
	public MemoryStoreFile deepCopy() {
		MemoryStoreFile cp = new MemoryStoreFile();
		this.doCopy(cp);
		return cp;
	}

	@Override
	public BaseStruct getOrAllocateField(String name) {
		// TODO consider this
		//if ("TextReader".equals(name))
		//	return new FileSystemTextReader(this);
		
		return super.getOrAllocateField(name);
	}
	
	@Override
	public ReturnOption operation(StackWork stack, XElement code) throws OperatingContextException {
		return super.operation(stack, code);
	}
		
	@Override
	public void readAllText(OperationOutcome<String> callback) {
		callback.returnValue((this.binary != null) ? this.binary.toString() : null);
	}
	
	@Override
	public void writeAllText(String v, OperationOutcomeEmpty callback) {
		this.binary = new Memory();
		this.binary.write(v);

		callback.returnResult();
	}
	
	@Override
	public void readAllBinary(OperationOutcome<Memory> callback) {
		callback.returnValue(this.binary);
	}
	
	@Override
	public void writeAllBinary(Memory v, OperationOutcomeEmpty callback) {
		this.binary = v;

		callback.returnResult();
	}

	@Override
	public void hash(String method, OperationOutcome<String> callback) {
		try {
			String res = HashUtil.hash(method, new InputWrapper(this.binary));
			
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
		this.binary = null;

		callback.returnResult();
	}

}
