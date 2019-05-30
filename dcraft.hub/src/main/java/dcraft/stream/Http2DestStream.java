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
package dcraft.stream;

import dcraft.filestore.FileStoreFile;
import dcraft.log.Logger;
import dcraft.stream.file.IFileStreamConsumer;
import dcraft.struct.RecordStruct;
import dcraft.task.IParentAwareWork;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import dcraft.filestore.FileDescriptor;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.stream.file.BaseFileStream;
import dcraft.stream.file.FileSlice;
import dcraft.task.TaskContext;
import dcraft.xml.XElement;

import java.util.function.Consumer;

public class Http2DestStream extends BaseFileStream implements IStreamDest<FileSlice>, IFileStreamConsumer {
    final static public int FOURKB = 48 * 1000;  // 1024; leave space for headers
    
	static public Http2DestStream of(Http2ConnectionHandler conn, ChannelHandlerContext ctx, int streamId) {
		Http2DestStream fds = new Http2DestStream();
		fds.conn = conn;
		fds.ctx = ctx;
		fds.streamId = streamId;
		return fds;
	}

	protected ChannelHandlerContext ctx = null;
	protected Http2ConnectionHandler conn = null;
	protected int streamId = 0;
	protected FileDescriptor fd = null;
	
	protected Http2DestStream() {
	}
	
	// for use with dcScript
	@Override
	public void init(IParentAwareWork stack, XElement el) {
		// TODO 
	}
	
	@Override
	public void close() throws OperatingContextException {
		//System.out.println("File DEST killed");	// TODO
		
		
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
		
		if (slice.getFile().isFolder()) {
			slice.release();
			OperationContext.getAsTaskOrThrow().kill("Folder cannot be stored into a http out");
			return ReturnOption.DONE;
		}
		
		FileDescriptor fd = slice.getFile();
		
		if ((this.fd != null) && (this.fd != fd)) {
			slice.release();
			OperationContext.getAsTaskOrThrow().kill("Cannot combine files into a http out");
			return ReturnOption.DONE;
		}
		
		if (slice.getData() != null) {
	        ChannelPromise dpromise = this.ctx.newPromise();
            
	        TaskContext tctx = OperationContext.getAsTaskOrThrow();
	        
	        dpromise.addListener(new GenericFutureListener<Future<? super Void>>() {
				@Override
				public void operationComplete(Future<? super Void> future) throws Exception {
					OperationContext.set(tctx);
					
					tctx.resume();
				}
			});
	        
	        // the data should be cleaned up in the pipeline
	        this.conn.encoder().writeData(this.ctx, streamId, slice.getData(), 0, slice.isEof(), dpromise);
			
	        try {
	            this.conn.flush(this.ctx);
	        }
	        catch (Throwable cause) {
				OperationContext.getAsTaskOrThrow().kill("Problem writing destination file: " + cause);
	        	this.conn.onError(this.ctx, true, cause);
				
	        	// hopefully the pipeline or onError will cleanup the slice, but in case not
	        	try {
	        		slice.release();
	        	}
	        	catch (Exception x){
	        		// ignore, nothing we can do
	        	}
	        	
				return ReturnOption.DONE;
	        }
		
			return ReturnOption.AWAIT;
		}
		
		return ReturnOption.CONTINUE;
	}

	@Override
	public void execute() throws OperatingContextException {
		// TODO optimize if upstream is local file also
		
		this.upstream.read();
	}
}
