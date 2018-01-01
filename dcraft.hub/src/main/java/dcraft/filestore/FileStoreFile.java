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

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.script.work.ReturnOption;
import dcraft.stream.IStreamDest;
import dcraft.stream.IStreamSource;
import dcraft.stream.file.FileSlice;
import dcraft.stream.file.IFileStreamDest;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.IParentAwareWork;
import dcraft.util.*;
import dcraft.xml.XElement;

import java.util.List;

abstract public class FileStoreFile extends FileDescriptor {
	protected FileStore driver = null;
	
	public FileStore getDriver() {
		return this.driver;
	}
	
	public boolean exists() {
		return this.getFieldAsBooleanOrFalse("Exists");
	}
	
	abstract public IFileCollection scanner() throws OperatingContextException;
	abstract public IFileStreamDest allocStreamDest() throws OperatingContextException;
	abstract public IFileStreamDest allocStreamDest(boolean relative) throws OperatingContextException;
	abstract public IStreamSource allocStreamSrc() throws OperatingContextException;
	abstract public void readAllText(OperationOutcome<String> callback) throws OperatingContextException;
	abstract public void writeAllText(String v, OperationOutcomeEmpty callback) throws OperatingContextException;
	abstract public void readAllBinary(OperationOutcome<Memory> callback) throws OperatingContextException;
	abstract public void writeAllBinary(Memory v, OperationOutcomeEmpty callback) throws OperatingContextException;
	abstract public void hash(String method, OperationOutcome<String> callback) throws OperatingContextException;
	abstract public void rename(String name, OperationOutcomeEmpty callback) throws OperatingContextException;
	abstract public void remove(OperationOutcomeEmpty callback) throws OperatingContextException;
	abstract public void getAttribute(String name, OperationOutcome<Struct> callback) throws OperatingContextException;
	abstract public void getFolderListing(OperationOutcome<List<FileStoreFile>> callback) throws OperatingContextException;
	abstract public RecordStruct getExtra() throws OperatingContextException;
	
	@Override
    protected void doCopy(Struct n) {
    	super.doCopy(n);
    	
    	FileStoreFile nn = (FileStoreFile)n;
		nn.driver = this.driver;
    }
	
	@Override
	public ReturnOption operation(IParentAwareWork stack, XElement code) throws OperatingContextException {
		/* TODO review
		if ("Hash".equals(code.getName())) {
			String meth = stack.stringFromElement(code, "Method");
			
	        final Struct var = stack.refFromElement(code, "Target");

			if (var instanceof ScalarStruct) { 				
				try {
					this.hash(meth, new OperationOutcome<String>() {	
						@Override
						public void callback(String result) throws OperatingContextException {
							((ScalarStruct)var).adaptValue(result);
							stack.resume();
						}
					});
					
					return;
				} 
				catch (OperatingContextException x) {
					Logger.error("Context error in Hash: " + x);
				}
			}
			else {
				Logger.error("Invalid hash target!");
			}
			
			stack.resume();
			return;
		}
		
		if ("Rename".equals(code.getName())) {
			String val = stack.stringFromElement(code, "Value");
			
			// TODO support other methods
			if (StringUtil.isEmpty(val)) {
				// TODO log
				stack.resume();
				return;
			}
			
			Path dest = this.localpath.getParent().resolve(val);
			
			try {
				Files.move(this.localpath, dest);
				
				this.localpath = dest;
				this.refreshProps();
			} 
			catch (IOException x) {
				// TODO catch?
			}
			
			stack.resume();
			return;
		}

		// this is kind of a hack - may want to re-evaluate this later
		// used by NCC provisioning
		if ("WriteText".equals(code.getName())) {
			String text = code.getText();
			
	        Struct content = StringUtil.isNotEmpty(text) 
	        		? stack.resolveValue(text)
	        		: stack.refFromElement(code, "Target");
	        
	        if (content != null) {
	        	IOUtil.saveEntireFile(this.localpath, Struct.objectToString(content));
	        	this.refreshProps();
	        }
		
			stack.resume();
			return;
		}

		// this is kind of a hack - may want to re-evaluate this later
		// used by NCC provisioning
		if ("ReadText".equals(code.getName())) {
			if (this.getFieldAsBooleanOrFalse("Exists")) {
		        final Struct var = stack.refFromElement(code, "Target");
	
		        //System.out.println("e: " + var);
		        
				if (var instanceof NullStruct) {					
			        String handle = stack.stringFromElement(code, "Handle");

					if (handle != null) 
			            stack.addVariable(handle, new StringStruct(IOUtil.readEntireFile(this.localpath)));
					
					// TODO log
				}			
				else if (var instanceof ScalarStruct) {					
					((ScalarStruct)var).adaptValue(IOUtil.readEntireFile(this.localpath));
				}
				else {
					// TODO log
				}
			}
			
			stack.resume();
			return;
		}

		if ("Delete".equals(code.getName())) {
			try {
				if (this.isFolder())
					FileUtil.deleteDirectory(this.localpath);
				else
					Files.deleteIfExists(this.localpath);
			} 
			catch (IOException x) {
				// TODO Auto-generated catch block
			}
			
	    	this.refreshProps();
	    	
			stack.resume();
			return;
		}
		*/
		
		return super.operation(stack, code);
	}
	
	@Override
	public boolean validate() {
		if (! this.hasExplicitType())
			return super.validate("dcFileStoreFile");
		
		return super.validate();
	}
}
