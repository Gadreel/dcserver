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

import dcraft.aws.s3.S3Object;
import dcraft.aws.s3.Utils;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileDescriptor;
import dcraft.filestore.FileStore;
import dcraft.filestore.FileStoreFile;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.scriptold.StackEntry;
import dcraft.stream.IStreamDest;
import dcraft.stream.ReturnOption;
import dcraft.stream.file.BaseFileStream;
import dcraft.stream.file.FileSlice;
import dcraft.stream.file.IFileStreamDest;
import dcraft.struct.Struct;
import dcraft.struct.scalar.NullStruct;
import dcraft.xml.XElement;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class AwsDestStream extends BaseFileStream implements IFileStreamDest {
	static public AwsDestStream from(AwsStoreFile file) {
		AwsDestStream fds = new AwsDestStream();
		fds.currfile = file;
		return fds;
	}
	
	protected OutputStream out = null;
	protected boolean userelpath = true;
	
	protected AwsDestStream() {
	}

	public AwsDestStream withRelative(boolean v) {
		this.userelpath = v;
		return this;
	}
	
	// for use with dcScript
	@Override
	public void init(StackEntry stack, XElement el) throws OperatingContextException {
			// TODO autorelative and rethink the RelativeTo
		if (stack.boolFromElement(el, "Relative", true) || el.getName().startsWith("X")) {
        	this.userelpath = true;
        }

        Struct src = stack.refFromElement(el, "RelativeTo");
        
        if ((src != null) && !(src instanceof NullStruct)) {
            if (src instanceof FileStore)
            	this.currfile = (AwsStoreFile) ((FileStore) src).rootFolder();
            else if (src instanceof FileStoreFile)
            	this.currfile = ((AwsStoreFile) src);
            
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
			return this.handleFolder(slice);
		
		return this.handleFile(slice);
	}
	
	public ReturnOption handleFile(FileSlice slice) throws OperatingContextException {
		if (slice.getFile().isFolder()) {
			slice.release();
			
			OperationContext.getAsTaskOrThrow().kill("Folder cannot be stored into a file");
			return ReturnOption.DONE;
		}
		
		if (slice.getData() != null) {
			if (this.out == null) {
				try {
					S3Object object = new S3Object(null, null);
					
					// TODO add meta to object, setup permissions
					
					HttpURLConnection request =
							((AwsStoreFile) this.currfile).awsdriver.connection.makeRequest("PUT", ((AwsStoreFile) this.currfile).getDriver().getBucket(),
									Utils.urlencode(((AwsStoreFile) this.currfile).resolveToKey()), null, null, object);
					
					request.setDoOutput(true);
					request.setFixedLengthStreamingMode(slice.getFile().getSize());
					
					this.out = request.getOutputStream();
				} 
				catch (IOException x) {
					slice.release();
					
					OperationContext.getAsTaskOrThrow().kill("Problem opening destination file: " + x);
					return ReturnOption.DONE;
				}
			}
			
			for (ByteBuffer buff : slice.getData().nioBuffers()) {
				try {
					this.out.write(buff.array(), buff.arrayOffset() + buff.position(), buff.limit());
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
				
				((AwsStoreFile) this.currfile).loadDetails(null);		// TODO really we want to wait, but this is fine for now
			}
			catch (IOException x) {
				OperationContext.getAsTaskOrThrow().kill("Problem closing destination file: " + x);
				return ReturnOption.DONE;
			}
		}
		
		return ReturnOption.CONTINUE;
	}
	
	public ReturnOption handleFolder(FileSlice slice) throws OperatingContextException {
		CommonPath fpath = new CommonPath(this.userelpath ? slice.getFile().getPath() : "/" + slice.getFile().getPathAsCommon().getFileName());
		
		if (slice.getFile().isFolder()) {
			/* we could, but AWS doen't care about folders
			TaskContext tctx = OperationContext.getAsTaskOrThrow();
			
			this.file.getDriver().addFolder(this.file.resolvePath(fpath), new OperationOutcome<FileStoreFile>() {
				@Override
				public void callback(FileStoreFile result) throws OperatingContextException {
					tctx.resume();
				}
			});
			
			return ReturnOption.AWAIT;
			*/
			
			return ReturnOption.CONTINUE;
		}

		if (this.out == null)
			try {
				long fsize = slice.getFile().getSize();
				
				System.out.println("Size: " + fsize);
				
				AwsStoreFile fs = AwsStoreFile.of(((AwsStoreFile) this.currfile).awsdriver, this.currfile.resolvePath(fpath), false);
				
				S3Object object = new S3Object(null, null);
				
				// TODO add meta to object, setup permissions
				
				HttpURLConnection request =
						((AwsStoreFile) this.currfile).awsdriver.connection.makeRequest("PUT", ((AwsStoreFile) this.currfile).getDriver().getBucket(),
								Utils.urlencode(fs.resolveToKey()), null, null, object);
				
				request.setDoOutput(true);
				request.setFixedLengthStreamingMode(fsize);
				
				this.out = request.getOutputStream();
			}
			catch (IOException x) {
				slice.release();
				
				OperationContext.getAsTaskOrThrow().kill("Problem opening destination file: " + x);
				return ReturnOption.DONE;
			}
		
		if (slice.getData() != null) {
			for (ByteBuffer buff : slice.getData().nioBuffers()) {
				try {
					byte[] b = buff.array();
					
					System.out.println("Buffer size: " + b.length);
					System.out.println("Buffer offset: " + buff.arrayOffset());
					System.out.println("Buffer limit: " + buff.limit());
					
					this.out.write(b, buff.arrayOffset() + buff.position(), buff.limit());
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
				
				((AwsStoreFile) this.currfile).loadDetails(null);		// TODO really we want to wait, but this is fine for now
			}
			catch (IOException x) {
				OperationContext.getAsTaskOrThrow().kill("Problem closing destination file stream: " + x);
				return ReturnOption.DONE;
			}
		}
		
		return ReturnOption.CONTINUE;
	}

	@Override
	public void execute() throws OperatingContextException {
		this.upstream.read();
	}
}
