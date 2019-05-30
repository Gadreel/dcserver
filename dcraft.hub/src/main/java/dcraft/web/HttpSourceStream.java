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
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.local.LocalStoreFile;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.stream.IStreamSource;
import dcraft.stream.ReturnOption;
import dcraft.stream.StreamUtil;
import dcraft.stream.file.BaseFileStream;
import dcraft.stream.file.FileSlice;
import dcraft.task.IParentAwareWork;
import dcraft.task.TaskContext;
import dcraft.util.TimeUtil;
import dcraft.xml.XElement;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.LastHttpContent;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class HttpSourceStream extends BaseFileStream implements IStreamSource, IContentDecoder {
	static public HttpSourceStream source(TaskContext ctx) {
		HttpSourceStream stream = new HttpSourceStream();
		stream.context = ctx;
		stream.currfile = FileDescriptor.of("/upload.bin")
			.withModificationTime(TimeUtil.now())
			.withSize(0)
			.withIsFolder(false);
		return stream;
	}

	protected long offset = 0;
	protected TaskContext context = null;
	protected boolean eof = false;

	protected HttpSourceStream() {
	}

	@Override
	public void offer(HttpContent chunk) throws OperatingContextException {
		OperationContext.set(this.context);

		this.eof = chunk instanceof LastHttpContent;
		
		ByteBuf buf = chunk.content();
		buf.retain(1);
		
		long amt = buf.readableBytes();

		this.addSlice(buf, this.offset, this.eof);

		this.offset += amt;

		this.context.resume();
	}

	@Override
	public void release() throws OperatingContextException {
		OperationContext.set(this.context);

		this.cleanup();
	}

	// for use with dcScript
	@Override
	public void init(IParentAwareWork stack, XElement el) {
		// anything we need to gleam from the xml?
	}
	
	@Override
	public void close() throws OperatingContextException {
		WebController controller = ((WebController) OperationContext.getAsTaskOrThrow().getController());

		controller.sendRequestOk();

		this.context = null;
		
		super.close();
	}
	
	/**
	 * Someone downstream wants more data
	 */
	@Override
	public void read() throws OperatingContextException {
		if (this.handlerFlush() != ReturnOption.CONTINUE)
			return;
		
		if (this.eof) {
			this.consumer.handle(FileSlice.FINAL);
			return;
		}
		
		Channel channel = ((WebController) OperationContext.getAsTaskOrThrow().getController()).getChannel();

		channel.read();
	}
}
