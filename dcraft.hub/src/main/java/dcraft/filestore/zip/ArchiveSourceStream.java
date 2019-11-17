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
package dcraft.filestore.zip;

import dcraft.filestore.FileStoreFile;
import dcraft.filestore.local.LocalStoreFile;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.stream.IStreamSource;
import dcraft.stream.ReturnOption;
import dcraft.stream.file.BaseFileStream;
import dcraft.stream.file.FileSlice;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;
import io.netty.buffer.ByteBuf;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

public class ArchiveSourceStream extends BaseFileStream implements IStreamSource {
	static public ArchiveSourceStream of(FileStoreFile src) {
		if (src.isFolder()) {
			Logger.error("ArchiveSourceStream is for files only, not folders.");
			return null;
		}

		ArchiveSourceStream stream = new ArchiveSourceStream();
		stream.currfile = src;
		return stream;
	}

	protected ZipArchiveInputStream in = null;
	protected long insize = 0;
	protected long inprog = 0;

	protected ArchiveSourceStream() {
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
		if (this.currfile instanceof ArchiveStoreFile) {
			this.readArchiveFile();
		}
		else {
			OperationContext.getAsTaskOrThrow().kill("Wrong file object type: " + this.currfile.getClass().getName());
		}
	}
	
	// release data if error
	public void readArchiveFile() throws OperatingContextException {
		ArchiveStoreFile fs = (ArchiveStoreFile) this.currfile;

		if (this.in == null) {
			// As a source we are responsible for progress tracking
			OperationContext.getAsTaskOrThrow().setAmountCompleted(0);
			
			this.in = fs.seekReadAccess();
			this.insize = fs.getSize();

			if (this.in == null) {
				OperationContext.getAsTaskOrThrow().kill("Unable to read source file ");
				return;
			}
		}
		
		while (true) {
			// TODO sizing?
	        ByteBuf data = ApplicationHub.getBufferAllocator().heapBuffer(32768);

	        int pos = -1;
			
	        try {
	        	long amount = data.capacity();
	        	long needed = this.insize - this.inprog;

	        	if (needed < amount)
	        		amount = needed;

				pos = this.in.read(data.array(), data.arrayOffset(), (int) amount);
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
	        	this.inprog  = this.insize;
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
