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
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardWatchEventKinds;
import java.util.function.Consumer;

import dcraft.filestore.FileDescriptor;
import dcraft.filestore.FileStore;
import dcraft.filestore.FileStoreFile;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.scriptold.StackEntry;
import dcraft.stream.IStreamDest;
import dcraft.stream.StreamUtil;
import dcraft.stream.file.BaseFileStream;
import dcraft.stream.file.FileSlice;
import dcraft.stream.ReturnOption;
import dcraft.stream.file.IFileStreamDest;
import dcraft.struct.Struct;
import dcraft.struct.scalar.NullStruct;
import dcraft.xml.XElement;

public class LocalDestStream extends BaseFileStream implements IFileStreamDest {
	static public LocalDestStream from(Path src) {
		LocalDestStream fds = new LocalDestStream();
		fds.currfile = StreamUtil.localFile(src);
		return fds;
	}

	static public LocalDestStream from(LocalStoreFile file) {
		LocalDestStream fds = new LocalDestStream();
		fds.currfile = file;
		return fds;
	}
	
	//protected LocalStoreFile file = null;
	protected FileChannel out = null;
	protected boolean userelpath = true;
	
	protected LocalDestStream() {
	}

	public LocalDestStream withRelative(boolean v) {
		this.userelpath = v;
		return this;
	}
	
	// for use with dcScript
	@Override
	public void init(StackEntry stack, XElement el) throws OperatingContextException {
			// TODO autorelative and re-think RelativeTo, shouldn't a stream always come from a file so this is redundant?
		if (stack.boolFromElement(el, "Relative", true) || el.getName().startsWith("X")) {
        	this.userelpath = true;
        }

        Struct src = stack.refFromElement(el, "RelativeTo");
        
        if ((src != null) && !(src instanceof NullStruct)) {
            if (src instanceof FileStore)
            	this.currfile = (LocalStoreFile) ((FileStore) src).rootFolder();
            else if (src instanceof FileStoreFile)
            	this.currfile = ((LocalStoreFile) src);
            
        	this.userelpath = true;
        }
	}
	
	@Override
	public void close() throws OperatingContextException {
		//System.out.println("File DEST killed");	// TODO
		
		if (this.out != null)
			try {
				this.out.close();
			} 
			catch (IOException x) {
			}
		
		this.out = null;
		
		super.close();
	}
	
	// TODO someday support Append and Resume type features
	
	@Override
	public ReturnOption handle(FileSlice slice) throws OperatingContextException {
		if (slice == FileSlice.FINAL) {
			// cleanup here because although we call task complete below, and task complete
			// also does cleanup, if we aer in a work chain that cleanup may not fire for a
			// while. This is the quicker way to let go of resources - but task end will also
			try {
				this.cleanup();
			}
			catch (Exception x) {
				Logger.warn("Stream cleanup did produced errors: " + x);
			}
			
			OperationContext.getAsTaskOrThrow().returnEmpty();
			return ReturnOption.DONE;
		}
		
		if (this.currfile.isFolder())
			return this.handleLocalFolder(slice);
		
		return this.handleLocalFile(slice);
	}
	
	public ReturnOption handleLocalFile(FileSlice slice) throws OperatingContextException {
		if (slice.getFile().isFolder()) {
			slice.release();
			
			OperationContext.getAsTaskOrThrow().kill("Folder cannot be stored into a file");
			return ReturnOption.DONE;
		}
		
		if (slice.getData() != null) {
			if (this.out == null) {
				try {
					Path dpath = ((LocalStoreFile) this.currfile).getLocalPath();
					
					Files.createDirectories(dpath.getParent());
					
					this.out = FileChannel.open(dpath, 
							StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC);
				} 
				catch (IOException x) {
					slice.release();
					
					OperationContext.getAsTaskOrThrow().kill("Problem opening destination file: " + x);
					return ReturnOption.DONE;
				}
			}
			
			for (ByteBuffer buff : slice.getData().nioBuffers()) {
				try {
					this.out.write(buff);
				} 
				catch (IOException x) {
					slice.release();
					OperationContext.getAsTaskOrThrow().kill("Problem writing destination file: " + x);
					return ReturnOption.DONE;
				}
			}
		
			slice.release();
		}
		
		if (slice.isEof()) {
			try {
				if (this.out != null) {
					this.out.close();
					this.out = null;
				}
				
				((LocalStoreFile) this.currfile).refreshProps();
				
				((LocalStoreFile) this.currfile).getDriver().fireFolderEvent(((LocalStoreFile) this.currfile).getLocalPath(), StandardWatchEventKinds.ENTRY_MODIFY);
			}
			catch (IOException x) {
				OperationContext.getAsTaskOrThrow().kill("Problem closing destination file: " + x);
				return ReturnOption.DONE;
			}
		}
		
		return ReturnOption.CONTINUE;
	}
	
	public ReturnOption handleLocalFolder(FileSlice slice) throws OperatingContextException {
		Path folder = ((LocalStoreFile) this.currfile).getLocalPath();
		
		if (Files.notExists(folder))
			try {
				Files.createDirectories(folder);
			}
			catch (IOException x) {
				slice.release();
				
				OperationContext.getAsTaskOrThrow().kill("Problem making destination top folder: " + x);
				return ReturnOption.DONE;
			}
		
		String fpath = (this.userelpath) ? slice.getFile().getPath() : "/" + slice.getFile().getPathAsCommon().getFileName();
		
		if (slice.getFile().isFolder()) {
			try {
				Files.createDirectories(folder.resolve(fpath.substring(1))); 
			} 
			catch (IOException x) {
				slice.release();
				
				OperationContext.getAsTaskOrThrow().kill("Problem making destination folder: " + x);
				return ReturnOption.DONE;
			}
			
			// don't count folders ? - deposits don't want them?
			//if (this.tabulator != null)
			//	this.tabulator.accept(slice.getFile());
			
			return ReturnOption.CONTINUE;
		}

		if (this.out == null)
			try {
				Path dpath = folder.resolve(fpath.substring(1));
				
				Files.createDirectories(dpath.getParent());
				
				this.out = FileChannel.open(dpath, 
						StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC);
			} 
			catch (IOException x) {
				slice.release();
				
				OperationContext.getAsTaskOrThrow().kill("Problem opening destination file: " + x);
				return ReturnOption.DONE;
			}
		
		if (slice.getData() != null) {
			for (ByteBuffer buff : slice.getData().nioBuffers()) {
				try {
					this.out.write(buff);
				} 
				catch (IOException x) {
					slice.release();
					OperationContext.getAsTaskOrThrow().kill("Problem writing destination file: " + x);
					return ReturnOption.DONE;
				}
			}
			
			slice.release();
		}
		
		if (slice.isEof()) {
			try {
				this.out.close();
				this.out = null;
				
				//if (this.tabulator != null)
				//	this.tabulator.accept(this.currfile);
				
				((LocalStoreFile) this.currfile).refreshProps();
			}
			catch (IOException x) {
				OperationContext.getAsTaskOrThrow().kill("Problem closing destination file: " + x);
				return ReturnOption.DONE;
			}
		}
		
		return ReturnOption.CONTINUE;
	}

	@Override
	public void execute() throws OperatingContextException {
		// TODO optimize if upstream is local file also
		
		this.upstream.read();
	}
}
