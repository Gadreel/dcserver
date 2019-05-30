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
package dcraft.filestore.aws;

import dcraft.aws.s3.Utils;
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

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;

public class AwsSourceStream extends BaseFileStream implements IStreamSource {
	static public AwsSourceStream of(AwsStoreFile src) {
		if (src.isFolder()) {
			Logger.error("AwsSourceStream is for files only, not folders.");
			return null;
		}
		
		AwsSourceStream stream = new AwsSourceStream();
		stream.currfile = src;
		return stream;
	}

	protected InputStream in = null;
	protected long insize = 0;
	protected long inprog = 0;
	protected boolean finalsent = false;
	
	protected AwsSourceStream() {
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
		if (this.finalsent)
			return;
		
		if (this.currfile == null) {
			this.finalsent = true;
			this.consumer.handle(FileSlice.FINAL);
			return;
		}
		
		// folders are handled in 1 msg, so we wouldn't get here in second or later call to a file
		if (! this.currfile.isFolder()) {
			this.readAwsFile();
		}
		else {
			OperationContext.getAsTaskOrThrow().kill("Wrong file object type: " + this.currfile.getClass().getName());
		}
	}
	
	// release data if error
	public void readAwsFile() throws OperatingContextException {
		if (this.finalsent)
			return;
		
		if (this.in == null) {
			this.insize = this.currfile.getSize();
			
			// As a source we are responsible for progress tracking
			OperationContext.getAsTaskOrThrow().setAmountCompleted(0);
			
			// TODO load meta data? S3Object object = new S3Object(null, null);
			
			try {
				HttpURLConnection request =
						((AwsStoreFile) this.currfile).getDriver().connection.makeRequest("GET", ((AwsStoreFile) this.currfile).getDriver().getBucket(),
								Utils.urlencode(((AwsStoreFile) this.currfile).resolveToKey()), null, null);
				
				if (request.getResponseCode() < 400) {
					this.in = request.getInputStream();
				}
				else {
					OperationContext.getAsTaskOrThrow().kill("Problem opening source file, code: " + request.getResponseCode());
					return;
				}
			}
			catch (IOException x) {
				OperationContext.getAsTaskOrThrow().kill("Problem opening source file: " + x);
				return;
			}
		}
		
		while (! this.finalsent) {
			// TODO sizing?
	        ByteBuf data = ApplicationHub.getBufferAllocator().heapBuffer(32768);
	        
	        int pos = -1;
			
	        try {
				pos = this.in.read(data.array(), data.arrayOffset(), data.capacity());
			}
			catch (IOException x1) {
				OperationContext.getAsTaskOrThrow().kill("Problem reading source file: " + x1);
				data.release();
				return;
			}
			
			// seems to happen though not clear why
			if (this.finalsent)
				break;
			
	        System.out.println("reading: " + this.currfile.getPath() + " from: " + this.inprog);
	        
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
	        	this.insize = 0;
	        }
	        else {
		        this.inprog += pos;
		        
		        data.writerIndex(pos);
		        
		        if (this.insize != 0)
			        OperationContext.getAsTaskOrThrow().setAmountCompleted((int)(this.inprog * 100 / this.insize));
	        }
	        
	    	if (this.consumer.handle(slice) != ReturnOption.CONTINUE)
	    		break;
	    	
	    	if (this.currfile == null) {
	    		this.finalsent = true;
				this.consumer.handle(FileSlice.FINAL);
	    		break;
	    	}
		}
	}
}
