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

import dcraft.filestore.FileStoreFile;
import dcraft.log.Logger;
import dcraft.stream.StreamUtil;
import dcraft.task.IParentAwareWork;
import io.netty.buffer.ByteBuf;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.stream.IStreamSource;
import dcraft.stream.ReturnOption;
import dcraft.stream.file.BaseFileStream;
import dcraft.stream.file.FileSlice;
import dcraft.xml.XElement;

public class LocalSourceStream extends BaseFileStream implements IStreamSource {
	static public LocalSourceStream of(Path src) {
		return LocalSourceStream.of(StreamUtil.localFile(src));
	}
	
	static public LocalSourceStream of(FileStoreFile src) {
		if (src.isFolder()) {
			Logger.error("LocalSourceStream is for files only, not folders.");
			return null;
		}
		
		LocalSourceStream stream = new LocalSourceStream();
		stream.currfile = src;
		return stream;
	}

	protected FileChannel in = null;
	protected long insize = 0;
	protected long inprog = 0;
	
	protected LocalSourceStream() {
	}
	
	// for use with dcScript
	@Override
	public void init(IParentAwareWork stack, XElement el) {
		// anything we need to gleam from the xml?
	}
	
	@Override
	public void close() throws OperatingContextException {
		//System.out.println("File SRC killed");	// TODO
		
		if (this.in != null)
			try {
				this.in.close();
			} 
			catch (IOException x) {
			}
		
		this.in = null;
		
		super.close();
	}
	
	// TODO skip to offset when opening file - if config says so

	/**
	 * Someone downstream wants more data
	 */
	@Override
	public void read() throws OperatingContextException {
		if (this.currfile == null) {
			this.consumer.handle(FileSlice.FINAL);
			return;
		}
		
		// folders are handled in 1 msg, so we wouldn't get here in second or later call to a file
		if (this.currfile instanceof LocalStoreFile) {
			this.readLocalFile();
		}
		else {
			OperationContext.getAsTaskOrThrow().kill("Wrong file object type: " + this.currfile.getClass().getName());
		}
	}
	
	// release data if error
	public void readLocalFile() throws OperatingContextException {
		LocalStoreFile fs = (LocalStoreFile) this.currfile;

		if (this.in == null) {
			// As a source we are responsible for progress tracking
			OperationContext.getAsTaskOrThrow().setAmountCompleted(0);
			
			try {
				this.in = FileChannel.open(fs.getLocalPath(), StandardOpenOption.READ);
				this.insize = Files.size(fs.getLocalPath());
			}
			catch (IOException x) {
				OperationContext.getAsTaskOrThrow().kill("Unable to read source file " + x);
				return;
			}
		}
		
		while (true) {
			// TODO sizing?
	        ByteBuf data = ApplicationHub.getBufferAllocator().heapBuffer(32768);
			
	        ByteBuffer buffer = ByteBuffer.wrap(data.array(), data.arrayOffset(), data.capacity());
	        
	        int pos = -1;
			
	        try {
				pos = this.in.read(buffer);
			} 
			catch (IOException x1) {
				OperationContext.getAsTaskOrThrow().kill("Problem reading source file: " + x1);
				data.release();
				return;
			}
			
			//System.out.println("reading: " + this.currfile.getPath() + " from: " + this.inprog);
			
	        FileSlice slice = FileSlice.allocate(this.currfile, data, 0, false);
	        
	        if (pos == -1) {
	        	try {
					this.in.close();
				} 
	        	catch (IOException x) {
	        		OperationContext.getAsTaskOrThrow().kill("Problem closing source file: " + x);
					data.release();
					return;
				}
	        	
	        	OperationContext.getAsTaskOrThrow().setAmountCompleted(100);
	        	
		        slice.setEof(true);
	        	
	        	this.currfile = null;
	        	this.in = null;
	        	this.inprog  = 100;
	        }
	        else {
		        this.inprog += pos;
		        
		        data.writerIndex(pos);
		        OperationContext.getAsTaskOrThrow().setAmountCompleted((int)(this.inprog * 100 / this.insize));
	        }
	        
	    	if (this.consumer.handle(slice) != ReturnOption.CONTINUE)
	    		break;
	    	
	    	if (this.currfile == null) {
				this.consumer.handle(FileSlice.FINAL);
	    		break;
	    	}
		}
	}
}
