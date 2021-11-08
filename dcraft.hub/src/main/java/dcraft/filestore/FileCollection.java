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
package dcraft.filestore;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcome;
import dcraft.log.Logger;
import dcraft.schema.SchemaHub;
import dcraft.script.StackUtil;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.stream.StreamUtil;
import dcraft.struct.BaseStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.xml.XElement;

/**
 * 
 * @author andy
 *
 */
public class FileCollection extends RecordStruct implements IFileCollection {
	protected List<FileStoreFile> collection = null;
	protected Predicate<FileStoreFile> filter = null;
	protected int pos = 0;
	
	public FileCollection() {
		this.withType(SchemaHub.getType("dcFileCollection"));
	}
	
	// does not engage filter
	public int getSize() {
		if (this.collection == null)
			return 0;
		
		return this.collection.size();
	}
	
	public void resetPosition() {
		this.pos = 0;
	}
	
	public FileCollection withPaths(Path... files) {
		for (Path f : files)
			this.withFiles(StreamUtil.localFile(f));
		
		return this;
	}
	
	public FileCollection withFiles(FileStoreFile... files) {
		if (this.collection == null)
			this.collection = new ArrayList<>();
		
		for (FileStoreFile f : files)
			this.collection.add(f);
		
		return this;
	}
	
	@Override
	public IFileCollection withFilter(Predicate<FileStoreFile> v) throws OperatingContextException {
		this.filter = v;
		return this;
	}
	
	@Override
	public void next(OperationOutcome<FileStoreFile> callback) throws OperatingContextException {
		callback.reset();
		
		while ((this.collection != null) && (this.pos < this.collection.size())) {
			FileStoreFile f = collection.get(this.pos++);
			
			if ((this.filter == null) || filter.test(f)) {
				callback.returnValue(f);
				return;
			}
		}

		callback.returnValue(null);
	}
	
	@Override
	public void forEach(OperationOutcome<FileStoreFile> callback) throws OperatingContextException {
		while ((this.collection != null) && (this.pos < this.collection.size())) {
			FileStoreFile f = collection.get(this.pos++);
			
			if ((this.filter == null) || filter.test(f))
				callback.returnValue(f);
			
			callback.reset();
		}
		
		this.pos = 0;
		
		callback.setResult(null);
	}

	@Override
	public ReturnOption operation(StackWork stack, XElement code) throws OperatingContextException {
        if ("WithFile".equals(code.getName())) {
			BaseStruct var = StackUtil.refFromElement(stack, code, "Value", true);

            if (var instanceof FileStoreFile) {
            	this.withFiles((FileStoreFile) var);
            }
            else if (var instanceof FileStore) {
            	this.withFiles(((FileStore) var).rootFolder());		// TODO really we want a scanner
            }
            else {
                Logger.error("Invalid hash target!");
            }

            return ReturnOption.CONTINUE;
        }

		return super.operation(stack, code);
	}

    @Override
    protected void doCopy(BaseStruct n) {
    	super.doCopy(n);
    	
    	FileCollection nn = (FileCollection)n;
		nn.collection = this.collection;
    }
    
	@Override
	public FileCollection deepCopy() {
		FileCollection cp = new FileCollection();
		this.doCopy(cp);
		return cp;
	}
}
