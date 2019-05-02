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

import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationController;
import dcraft.hub.op.UserContext;
import dcraft.struct.FieldStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.BooleanStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.util.StringUtil;
import dcraft.util.io.ByteBufWriter;
import dcraft.util.web.ContentTypeParser;
import dcraft.web.ui.UIUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedInput;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.cert.X509Certificate;

import dcraft.filestore.CommonPath;
import dcraft.log.Logger;
import dcraft.session.Session;
import dcraft.util.KeyUtil;
import dcraft.xml.XElement;

public class WebController extends OperationController {
	// TODO move to a Record Based Model

	public static WebController forChannel(Channel channel, XElement serviceSettings) {
		WebController ctrl = new WebController();
		
		ctrl.chan = new WeakReference<>(channel);
		ctrl.serviceSettings = serviceSettings;
		
		return ctrl;
	}
	
	protected WeakReference<Channel> chan = null;

	// TODO switch some/many members to store in "this" Records

    // used with HTTP only, not WS
	protected IContentDecoder decoder = null;    	
    protected boolean secure = false;
    
    protected XElement serviceSettings = null;

	public WebController() {
		super(OperationContext.allocateOpId());
	}
    
    public Response getResponse() {
		return (Response) this.getField("Response");
	}
	
	public boolean isSecure() {
		return this.secure;
	}
	
	public void setSecure(boolean v) {
		this.secure = v;
	}
	
	public XElement getServiceSettings() {
		return this.serviceSettings;
	}
	
	public void setServiceSettings(XElement v) {
		this.serviceSettings = v;
	}
	
	public Channel getChannel() {
		if (this.chan != null)
			return this.chan.get();
		
		return null;
	}
	
    public void setDecoder(IContentDecoder v) {
		this.decoder = v;
	}
    
    public IContentDecoder getDecoder() {
		return this.decoder;
	}
    
	public void initWeb(ChannelHandlerContext ctx, HttpRequest req) {
		this.secure = (ctx.channel().pipeline().get("ssl") != null);

        this.with("Request", WebController.loadRequest(ctx, req, this.secure));

		if (Logger.isDebug())
			Logger.debug("Web server request b2");

		Response response = new Response();
        response.load(ctx, req);
    	this.with("Response", response);
	}
	
	static public RecordStruct loadRequest(ChannelHandlerContext ctx, HttpRequest req, boolean secure) {
		RecordStruct ret = RecordStruct.record();
		
		ret.with("Method", req.getMethod());
		
		RecordStruct headers = RecordStruct.record();
		
		for (Map.Entry<String,String> entry : req.headers().entries()) {
			// TODO decode?
			
			headers.with(entry.getKey(), entry.getValue());
		}
		
		ret.with("Headers", headers);
		
		String hostlong = req.headers().get("Host");
		
		if (StringUtil.isNotEmpty(hostlong)) {
			int pos = hostlong.indexOf(':');

			if (pos == -1)
				ret
						.with("Host", hostlong)
						.with("Port", secure ? "443" : "80");
			else
				ret
						.with("Host", hostlong.substring(0, pos))
						.with("Port", hostlong.substring(pos + 1));
		}

		if (Logger.isDebug())
			Logger.debug("Web server request b1");

		String value = req.headers().get("Cookie");
		
		if (StringUtil.isNotEmpty(value)) {
			Set<Cookie> cset = ServerCookieDecoder.STRICT.decode(value);
			
			RecordStruct cookies = RecordStruct.record();
			
			for (Cookie cookie : cset)
				cookies.with(cookie.name(), cookie.value());
			
			ret.with("Cookies", cookies);
			
			if (cookies.isNotFieldEmpty("dcView"))
				ret.with("View", cookies.getFieldAsString("dcView"));
		}
		
		RecordStruct parameters = RecordStruct.record();
		
		QueryStringDecoder decoderQuery = new QueryStringDecoder(req.getUri());
		Map<String, List<String>> params = decoderQuery.parameters();        // TODO decode
		
		for (Map.Entry<String, List<String>> entry : params.entrySet()) {
			parameters.with(entry.getKey(), ListStruct.list().withCollection(entry.getValue()));
		}
		
		ret.with("Parameters", parameters);
		
		String mode = parameters.isFieldEmpty("_dcui")
				? "html" : parameters.getFieldAsList("_dcui").getItemAsString(0);
		
		ret
				.with("IsDynamic", "dyn".equals(mode))
				.with("Mode", mode);
		
		String path = QueryStringDecoder.decodeComponent(decoderQuery.path());
		
		ret
				.with("Path", path)
				.with("OriginalPath", path)
				.with("ContentType", new ContentTypeParser(headers.getFieldAsString("Content-Type")))
				.with("ContentLength", headers.getFieldAsString("Content-Length"));
		
		if (Logger.isDebug()) {
			Logger.debug("Request Path " + ret.getFieldAsString("Path"));
			Logger.debug("Request Method " + ret.getFieldAsString("Method"));
			
			for (FieldStruct ent : headers.getFields()) {
				Logger.debug("Request header: " + ent.getName() + ": " + ent.getValue());
			}
		}
		
		return ret;
	}
	
	public void initSearch(CommonPath path) {
		this.secure = true;
		
		this.with("Request", WebController.loadVoidRequest(path));
		
		if (Logger.isDebug())
			Logger.debug("Web server request b2");
		
		Response response = new Response();
		this.with("Response", response);
	}
	
	static public RecordStruct loadVoidRequest(CommonPath path) {
		return RecordStruct.record()
				.with("Path", path.toString())
				.with("OriginalPath", path)
				.with("Method", "GET")
				.with("Headers", RecordStruct.record())
				.with("Cookies", RecordStruct.record())
				.with("Parameters", RecordStruct.record());
	}
	
	public void offerContent(HttpContent v) throws OperatingContextException {
		IContentDecoder d = this.decoder;
		
		if (d != null) 
			d.offer(v);
		else {
			Channel tchan = this.getChannel();
			
			if (tchan != null)
				tchan.read();
		}
		
		//if (v instanceof LastHttpContent)
		//	this.decoder = null;
	}
	
	public void close() {
		try {
			Channel tchan = this.getChannel();
			
			if (tchan != null)
				tchan.close().await(2000);
		} 
		catch (InterruptedException x) {
			// ignore 
		}
	}
	
	public void sendNotFound() {
    	if (Logger.isDebug())
    		Logger.debug("Web server respond with Not Found");
    	
		if (this.getResponse() != null) {
			this.getResponse().setStatus(HttpResponseStatus.NOT_FOUND);
			this.send();
		}
	}
	
	public void sendNotFoundRead() {
    	if (Logger.isDebug())
    		Logger.debug("Web server respond with Not Found");
    	
		if (this.getResponse() != null) {
			this.getResponse().setStatus(HttpResponseStatus.NOT_FOUND);
			this.sendRead();
		}
	}
	
	public void sendForbidden() {
    	if (Logger.isDebug())
    		Logger.debug("Web server respond with Forbidden");
    	
		if (this.getResponse() != null) {
			this.getResponse().setStatus(HttpResponseStatus.FORBIDDEN);
			this.getResponse().setKeepAlive(false);
			this.send();
		}
	}
	
	public void sendForbiddenRead() {
    	if (Logger.isDebug())
    		Logger.debug("Web server respond with Forbidden");
    	
		if (this.getResponse() != null) {
			this.getResponse().setStatus(HttpResponseStatus.FORBIDDEN);
			this.getResponse().setKeepAlive(false);
			this.sendRead();
		}
	}
	
	public void sendInternalError() {
    	if (Logger.isDebug())
    		Logger.debug("Web server respond with Internal Server Error");
    	
		if (this.getResponse() != null) {
			this.getResponse().setStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR);
			this.getResponse().setKeepAlive(false);
			this.send();
		}
	}
	
	public void sendInternalErrorRead() {
    	if (Logger.isDebug())
    		Logger.debug("Web server respond with Internal Server Error");
    	
		if (this.getResponse() != null) {
			this.getResponse().setStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR);
			this.getResponse().setKeepAlive(false);
			this.sendRead();
		}
	}
	
	public void sendRequestBad() {
    	if (Logger.isDebug())
    		Logger.debug("Web server respond with Request Bad");
    	
		if (this.getResponse() != null) {
			this.getResponse().setStatus(HttpResponseStatus.BAD_REQUEST);
			this.getResponse().setKeepAlive(false);
			this.send();
		}
	}
	
	// read probably not needed, but just to be good
	public void sendRequestBadRead() {
		if (Logger.isDebug())
			Logger.debug("Web server respond with Request Bad");
		
		if (this.getResponse() != null) {
			this.getResponse().setStatus(HttpResponseStatus.BAD_REQUEST);
			this.getResponse().setKeepAlive(false);
			this.sendRead();
		}
	}
	
	public void sendNotModified() {
    	if (Logger.isDebug())
    		Logger.debug("Web server respond with Not Modified");
    	
		if (this.getResponse() != null) {
			this.getResponse().setStatus(HttpResponseStatus.NOT_MODIFIED);

			Channel tchan = this.getChannel();
			
			if ((tchan != null) && (this.getResponse() != null))
				this.getResponse().writeNotModified(tchan);
		}
	}

	public void sendRequestOkClose() {
		if (this.getResponse() != null) {
			this.getResponse().setStatus(HttpResponseStatus.OK);
			this.getResponse().setKeepAlive(false);
			this.send();
		}
	}
	
	public void sendRequestOk() {
		if (this.getResponse() != null) {
			this.getResponse().setStatus(HttpResponseStatus.OK);
			//this.response.setKeepAlive(true);
			this.send();
		}
	}
	
	public void sendRequestOkRead() {
		if (this.getResponse() != null) {
			this.getResponse().setStatus(HttpResponseStatus.OK);
			//this.response.setKeepAlive(true);
			this.sendRead();
		}
	}
	
	public void send() {
		//if ((this.chan != null) && this.chan.isWritable() && (this.response != null)) 
		Channel tchan = this.getChannel();
		
		if ((tchan != null) && (this.getResponse() != null))
			this.getResponse().write(tchan);
	}
	
	public void sendRead() {
		//if ((this.chan != null) && this.chan.isWritable() && (this.response != null))
		Channel tchan = this.getChannel();
		
		if ((tchan != null) && (this.getResponse() != null)) {
			this.getResponse().write(tchan);
			this.getChannel().read();
		}
	}
	
	public void sendStart(long contentLength) {
		Channel tchan = this.getChannel();
		
		if ((tchan != null) && (this.getResponse() != null))
			this.getResponse().writeStart(tchan, contentLength);
	}
	
	public void send(ByteBufWriter content) {
		this.send(content.getByteBuf());	// no need to release, that will be done by the channel's write
	}
	
	// this must release what ever is passed in
	public void send(ByteBuf content) {
		Channel tchan = this.getChannel();
		
		if (tchan != null)
			tchan.write(new DefaultHttpContent(content));
	}

	public void send(ChunkedInput<HttpContent> content) {
		Channel tchan = this.getChannel();
		
		if (tchan != null)
			tchan.write(content);
		
		/* TODO we don't need this?
		.addListener(new GenericFutureListener<Future<? super Void>>() {
				@Override
				public void operationComplete(Future<? super Void> future)
						throws Exception {
					//System.out.println("Sending an end");
					//HttpContext.this.response.writeEnd(HttpContext.this.chan);
				}
			});
			*/
	}
	
	public void sendMessage(RecordStruct msg) {
		try {
			//if ((this.chan != null) && this.chan.isWritable()) {
			Channel tchan = this.getChannel();
			
			if (tchan != null) {
				// include the version hash for the current deployed files
				//m.setField("DeployVersion", this.siteman.getVersion());
				
				// we are always using UTF 8, charset is required with any "text/*" mime type that is not ANSI text
				this.getResponse().setHeader(HttpHeaders.Names.CONTENT_TYPE, ResourceHub.getResources().getMime().getMimeType("json").getMimeType() + "; charset=utf-8");
				
				// TODO enable CORS - http://www.html5rocks.com/en/tutorials/file/xhr2/
				// TODO possibly config to be more secure for some users - see CORS handler in Netty
				this.getResponse().setHeader(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
				
				this.getResponse().setBody(msg);
				this.getResponse().write(tchan);
			}
		}
		catch (Exception x) {
		}
	}
	
	public void sendEnd() {
		Channel tchan = this.getChannel();

		if ((tchan != null) && (this.getResponse() != null))
			this.getResponse().writeEnd(tchan);
	}
	
	public void sendChunked() {
		//if ((this.chan != null) && this.chan.isWritable() && (this.response != null)) 
		Channel tchan = this.getChannel();
		
		if ((tchan != null) && (this.getResponse() != null))
			this.getResponse().writeChunked(tchan);
	}
	
	public void sendDownloadHeaders(String name, String mime) {
		//if ((this.chan != null) && this.chan.isWritable() && (this.response != null)) 
		Channel tchan = this.getChannel();
		
		if ((tchan != null) && (this.getResponse() != null))
			this.getResponse().writeDownloadHeaders(tchan, name, mime);
	}

	/*
	public void send(HttpContent chunk) {
		try {
			Channel tchan = this.getChannel();
			
			if (tchan != null) 
				tchan.writeAndFlush(chunk);   // we do not need to sync - HTTP is one request, one response.  we would not pile messages on this channel
		}
		catch (Exception x) {
		}
	}
	
	public void sendDownload(HttpContent chunk) {
		try {
			Channel tchan = this.getChannel();
			
			if (tchan != null)
				tchan.writeAndFlush(chunk).sync();    // for downloads we do need sync so we don't overwhelm client
			
			// TODO see if we can use something other than sync - http://normanmaurer.me/presentations/2014-facebook-eng-netty/slides.html#10.0
		}
		catch (Exception x) {
		}
	}
	*/

	public void closed() throws OperatingContextException {
		IContentDecoder d = this.decoder;
		
		if (d != null) {
			d.release();
			this.decoder = null;
		}
	}
	
	// get the thumbprint of client cert, if available
	public String getClientCert() {
		Channel tchan = this.getChannel();
		
		if (tchan != null) {
			SslHandler sslhandler = (SslHandler) tchan.pipeline().get("ssl");
			
			if (sslhandler != null) {
				try {
					X509Certificate[] list = sslhandler.engine().getSession().getPeerCertificateChain();
					
					if (list.length > 0) {
						String thumbprint = KeyUtil.getCertThumbprint(list[0]); 
						
						//System.out.println("got thumbprint: " + thumbprint);
						
						return thumbprint;
					}
				}
				catch (SSLPeerUnverifiedException x) {
					// ignore, at this point we don't enforce peer certs
				}
			}
		}
		
		return null;
	}	
}
