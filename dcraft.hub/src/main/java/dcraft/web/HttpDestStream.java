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
package dcraft.web;

import dcraft.filestore.FileDescriptor;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.log.count.CountHub;
import dcraft.stream.IStreamDest;
import dcraft.stream.ReturnOption;
import dcraft.stream.file.BaseFileStream;
import dcraft.stream.file.FileSlice;
import dcraft.stream.file.IFileStreamDest;
import dcraft.struct.RecordStruct;
import dcraft.task.TaskContext;
import dcraft.util.web.DateParser;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.*;
import io.netty.util.concurrent.GenericFutureListener;

import java.nio.file.Files;
import java.util.function.Consumer;

public class HttpDestStream extends BaseFileStream implements IFileStreamDest, GenericFutureListener<ChannelFuture> {
	static public HttpDestStream dest() {
		HttpDestStream fds = new HttpDestStream();
		return fds;
	}

	protected boolean headersent = false;
	protected boolean asAttachment = true;

	protected Consumer<FileDescriptor> tabulator = null;
	
	public void setHeaderSent(boolean v) {
		this.headersent = v;
	}

	public HttpDestStream withAsAttachment(boolean v) {
		this.asAttachment = v;
		return this;
	}

	@Override
	public IStreamDest<FileSlice> withTabulator(Consumer<FileDescriptor> v) throws OperatingContextException {
		this.tabulator = v;
		return this;
	}

	protected HttpDestStream() {
	}

	@Override
	public void close() throws OperatingContextException {
		//System.out.println("File DEST killed");	// TODO
		Channel channel = ((WebController) OperationContext.getAsTaskOrThrow().getController()).getChannel();

		ChannelFuture future2 = channel.writeAndFlush(new DefaultLastHttpContent());

		future2.addListener(new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture channelFuture) throws Exception {
				channel.read();
			}
		});

		super.close();
	}
	
	// TODO someday support Append and Resume type features
	
	@Override
	public ReturnOption handle(FileSlice slice) throws OperatingContextException {
		if (slice == FileSlice.FINAL) {
			// cleanup here because although we call task complete below, and task complete
			// also does cleanup, if we are in a work chain that cleanup may not fire for a
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

			OperationContext.getAsTaskOrThrow().kill("Folder cannot be downloaded in binary HTTP stream");
			return ReturnOption.DONE;
		}

		TaskContext taskContext = OperationContext.getAsTaskOrThrow();

		WebController wctrl = (WebController) taskContext.getController();

		Channel channel = wctrl.getChannel();

		if (! this.headersent) {
			this.headersent = true;

			long when = System.currentTimeMillis();

			String mtype = ResourceHub.getResources().getMime().getMimeTypeForPath(slice.getFile().getPathAsCommon()).getType();

			if (this.asAttachment) {
				wctrl.sendDownloadHeaders(slice.getFile().getPath() != null ? slice.getFile().getPathAsCommon().getFileName() : null, mtype);
			}
			else {
				HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
				
				response.headers().set(HttpHeaderNames.CONTENT_TYPE, mtype);
				response.headers().set("Date", new DateParser().convert(when));
				response.headers().set("Last-Modified", new DateParser().convert(when));
				response.headers().set("X-UA-Compatible", "IE=Edge,chrome=1");
				response.headers().set("Cache-Control", "no-cache");
				response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);

				// Write the response.
				channel.writeAndFlush(response);
			}
		}

		if (slice.getData() != null) {
			HttpContent b = new DefaultHttpContent(Unpooled.copiedBuffer(slice.getData()));		// TODO not copied
			ChannelFuture future = channel.writeAndFlush(b);

			future.addListener(new ChannelFutureListener() {
				@Override
				public void operationComplete(ChannelFuture channelFuture) throws Exception {
					taskContext.resume();  // cause an upstream read
				}
			});

			if (slice.isEof()) {
				if (HttpDestStream.this.tabulator != null)
					HttpDestStream.this.tabulator.accept(slice.getFile());
			}

			return ReturnOption.AWAIT;
		}

		if (slice.isEof()) {
			if (HttpDestStream.this.tabulator != null)
				HttpDestStream.this.tabulator.accept(slice.getFile());
		}

		return ReturnOption.CONTINUE;
	}

	// wait for a write
	@Override
	public void operationComplete(ChannelFuture channelFuture) throws Exception {

	}

	@Override
	public void execute() throws OperatingContextException {
		this.upstream.read();
	}
}
