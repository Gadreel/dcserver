/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package dcraft.test.http2.helloworld.server;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import dcraft.filestore.local.LocalSourceStream;
import dcraft.stream.Http2DestStream;
import dcraft.stream.StreamWork;
import dcraft.task.Task;
import dcraft.task.TaskHub;
import dcraft.util.IOUtil;
import dcraft.util.Memory;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2FrameListener;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.netty.buffer.Unpooled.unreleasableBuffer;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;

/**
 * A simple handler that responds with the message "Hello World!".
 */
public final class HelloWorldHttp2Handler extends Http2ConnectionHandler implements Http2FrameListener {

    static final ByteBuf RESPONSE_BYTES = unreleasableBuffer(copiedBuffer("Hello World", CharsetUtil.UTF_8));

    HelloWorldHttp2Handler(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder,
                           Http2Settings initialSettings) {
        super(decoder, encoder, initialSettings);
    }

    private static Http2Headers http1HeadersToHttp2Headers(FullHttpRequest request) {
        return new DefaultHttp2Headers()
                .authority(request.headers().get("Host"))
                .method("GET")
                .path(request.uri())
                .scheme("http");
    }

    /**
     * Handles the cleartext HTTP upgrade event. If an upgrade occurred, sends a simple response via HTTP/2
     * on stream 1 (the stream specifically reserved for cleartext HTTP upgrade).
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof HttpServerUpgradeHandler.UpgradeEvent) {
            HttpServerUpgradeHandler.UpgradeEvent upgradeEvent =
                    (HttpServerUpgradeHandler.UpgradeEvent) evt;
            onHeadersRead(ctx, 1, http1HeadersToHttp2Headers(upgradeEvent.upgradeRequest()), 0 , true);
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        cause.printStackTrace();
        ctx.close();
    }

    /**
     * Sends a "Hello World" DATA frame to the client.
     */
    private void sendResponse(ChannelHandlerContext ctx, int streamId, ByteBuf payload) {
        // Send a frame for the response status
        Http2Headers headers = new DefaultHttp2Headers().status(OK.codeAsText());
        encoder().writeHeaders(ctx, streamId, headers, 0, false, ctx.newPromise());
        encoder().writeData(ctx, streamId, payload, 0, true, ctx.newPromise());
        try {
            flush(ctx);
        } catch (Throwable cause) {
            onError(ctx, cause);
        }
    }

    // apparently not used
    @Override
    public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) {
        int processed = data.readableBytes() + padding;
        if (endOfStream) {
            sendResponse(ctx, streamId, data.retain());
        }
        return processed;
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId,
                              Http2Headers headers, int padding, boolean endOfStream) {
        if (endOfStream) {
        	String rpath = headers.get(":path").toString();
        	
        	System.out.println("path: " + rpath);
        	
        	if ("/test".equals(rpath)) {
        		this.multiparttest(ctx, streamId);
        		return;
        	}
        	
        	if ("/teststream".equals(rpath)) {
        		this.multipartteststream(ctx, streamId);
        		return;
        	}
        	
            ByteBuf content = ctx.alloc().buffer();
            content.writeBytes(RESPONSE_BYTES.duplicate());
            ByteBufUtil.writeAscii(content, " - via HTTP/2");
            sendResponse(ctx, streamId, content);
        }
    }
    
    public void multiparttest(ChannelHandlerContext ctx, int streamId) {
    	Path pp = Paths.get("/Work/Projects/divconq/hub/public/dcw/hhm/www/imgs/share.jpg");
    	
    	Memory mem = IOUtil.readEntireFileToMemory(pp);
    	
        Http2Headers headers = new DefaultHttp2Headers()
        	.status(OK.codeAsText())
        	.add("content-type", "image/jpeg");
        
        ChannelPromise hpromise = ctx.newPromise();
        
        int fourk = 48 * 1000;  // 1024; leave space for headers
        AtomicInteger srcidx = new AtomicInteger();
        AtomicBoolean srcdone = new AtomicBoolean();
        
        hpromise.addListener(new GenericFutureListener<Future<? super Void>>() {
			@Override
			public void operationComplete(Future<? super Void> future) throws Exception {
				if (srcdone.get())
					return;
				
		        ChannelPromise dpromise = ctx.newPromise();
		        
		        dpromise.addListener(this);
		    	
		        ByteBuf content = ctx.alloc().buffer();
		        
		        int amt = mem.getLength() - srcidx.get();
		        
		        if (amt > fourk)
		        	amt = fourk;
		        else
		        	srcdone.set(true);
		        
		        content.writeBytes(mem.toArray(), srcidx.get(), amt);		// toArray not efficient, just demonstrates a point
		        
		        encoder().writeData(ctx, streamId, content, 0, srcdone.get(), dpromise);
		        
		        srcidx.set(srcidx.get() + amt);
		        
		        System.out.println("writing jpg: " + amt + " - " + srcdone.get());
		        
		        try {
		            flush(ctx);
		        } catch (Throwable cause) {
		            onError(ctx, cause);
		        }
			}
		});
        
        encoder().writeHeaders(ctx, streamId, headers, 0, false, hpromise);
    }
    
    public void multipartteststream(ChannelHandlerContext ctx, int streamId) {
    	Path pp = Paths.get("/Work/Projects/divconq/hub/public/dcw/hhm/www/imgs/share.jpg");
    	
        Http2Headers headers = new DefaultHttp2Headers()
        	.status(OK.codeAsText())
        	.add("content-type", "image/jpeg");
        
        ChannelPromise hpromise = ctx.newPromise();
        
        hpromise.addListener(new GenericFutureListener<Future<? super Void>>() {
			@Override
			public void operationComplete(Future<? super Void> future) throws Exception {
				Task task = Task.ofHubRoot()
						.withTitle("Serve jpeg to web client")
						.withNextId("WEB")
						.withWork(StreamWork.of( 
							LocalSourceStream.of(pp),
							Http2DestStream.of(HelloWorldHttp2Handler.this, ctx, streamId)
						));

				TaskHub.submit(task);
			}
		});
        
        encoder().writeHeaders(ctx, streamId, headers, 0, false, hpromise);
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency,
                              short weight, boolean exclusive, int padding, boolean endOfStream) {
        onHeadersRead(ctx, streamId, headers, padding, endOfStream);
    }

    @Override
    public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency,
                               short weight, boolean exclusive) {
    }

    @Override
    public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) {
    }

    @Override
    public void onSettingsAckRead(ChannelHandlerContext ctx) {
    }

    @Override
    public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) {
    }

    @Override
    public void onPingRead(ChannelHandlerContext ctx, ByteBuf data) {
    }

    @Override
    public void onPingAckRead(ChannelHandlerContext ctx, ByteBuf data) {
    }

    @Override
    public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId,
                                  Http2Headers headers, int padding) {
    }

    @Override
    public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData) {
    }

    @Override
    public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement) {
    }

    @Override
    public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId,
                               Http2Flags flags, ByteBuf payload) {
    }
}
