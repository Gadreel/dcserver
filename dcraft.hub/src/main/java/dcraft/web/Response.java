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
import dcraft.script.StackUtil;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.struct.CompositeParser;
import dcraft.struct.FieldStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.StringStruct;
import dcraft.util.Memory;
import dcraft.util.io.InputWrapper;
import dcraft.util.io.OutputWrapper;
import dcraft.util.web.DateParser;
import dcraft.xml.XElement;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.cookie.CookieHeaderNames;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import dcraft.log.Logger;
import dcraft.util.FileUtil;
import dcraft.util.StringUtil;
import io.netty.util.NetUtil;

public class Response extends RecordStruct {
	// TODO move to a Record Based Model
	
    protected Map<String, Cookie> cookies = new HashMap<>();
    protected boolean keepAlive = false; 
    protected boolean sendCookies = true;
    // TODO improve
	protected Memory body = new Memory();
    //protected Map<CharSequence, String> headers = new HashMap<>();
    //protected String status = "OK";
	
	// TODO improve
    protected PrintStream stream = null;
	
	// TODO improve
    public PrintStream getPrintStream() {
    	if (this.stream == null)
			try {
				this.stream = new PrintStream(new OutputWrapper(this.body), true, "UTF-8");
			} 
    		catch (UnsupportedEncodingException x) {
				// ignore, utf8 is supported
			}
    	
    	return this.stream;
    }
    
    public void setCookie(Cookie v) {
    	this.cookies.put(v.name(), v);
    }
    
    public void setHeader(String name, String value) {
    	if (! this.hasField("Headers"))
    		this.with("Headers", RecordStruct.record());
    	
    	this.getFieldAsRecord("Headers").with(name, value);
    }

	public String getHeader(String name) {
		if (! this.hasField("Headers"))
			return null;

		return this.getFieldAsRecord("Headers").getFieldAsString(name);
	}

    public void setDateHeader(String name, long value) {
    	DateParser parser = new DateParser();
    	this.setHeader(name, parser.convert(value));
    }

    public void setStatus(HttpResponseStatus v) {
		this.with("Code", v.code());
	}
    
    public void setCode(int v) {
		this.with("Code", v);
	}
    
    public void setKeepAlive(boolean v) {
		this.keepAlive = v;
	}

    public void setSendCookies(boolean v) {
		this.sendCookies = v;
	}

    public void write(PrintStream out) {
        this.body.setPosition(0);
        this.body.copyToStream(out);
	}
	
	// TODO prefer not to write this way - no full response
    public void write(Channel ch) {
    	int code = (int) this.getFieldAsInteger("Code", 200);

		HttpResponseStatus status = HttpResponseStatus.valueOf(code);

        if (! "OK".equals(status) && (this.body.getLength() == 0))
        	this.body.write(status.toString());
		
        // Build the response object.
    	FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);

        int clen = 0;
        this.body.setPosition(0);
        
		try {
			clen = response.content().writeBytes(new InputWrapper(this.body), this.body.getLength());
		} 
		catch (IOException e) {
		}
    	
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");

        if (this.keepAlive) {
            // Add 'Content-Length' header only for a keep-alive connection.
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, clen);
        	
            // Add keep alive header as per:
            // - http://www.w3.org/Protocols/HTTP/1.1/draft-ietf-http-v11-spec-01.html#Connection
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        }

        // Encode the cookies
		if (this.sendCookies) {
			for (Cookie c : this.cookies.values())
				response.headers().add(HttpHeaderNames.SET_COOKIE, ServerCookieEncoder.STRICT.encode(c));
		}

		RecordStruct headers = this.getFieldAsRecord("Headers");
		
		if (headers != null)
			for (FieldStruct h : headers.getFields())
				response.headers().set(h.getName(), Struct.objectToString(h.getValue()));
	
		// TODO restore - Hub.instance.getSecurityPolicy().hardenHttpResponse(response);
        
    	if (Logger.isDebug()) {
    		Logger.debug("Web server responding to " + ch.remoteAddress());
    		
    		for (Entry<String, String> ent : response.headers().entries()) {
        		Logger.debug("Response header: " + ent.getKey() + ": " + ent.getValue());
    		}
    	}
    	
        // Write the response.
        ChannelFuture future = ch.writeAndFlush(response);

        // Close the non-keep-alive connection after the write operation is done.
        if (! this.keepAlive)
            future.addListener(ChannelFutureListener.CLOSE);
        else
        	future.addListener(new ChannelFutureListener() {
				@Override
				public void operationComplete(ChannelFuture channelFuture) throws Exception {
					ch.read();
				}
			});
    }
    
    public void writeNotModified(Channel ch) {
		int code = (int) this.getFieldAsInteger("Code", 200);

		HttpResponseStatus status = HttpResponseStatus.valueOf(code);

		// Build the response object.
    	FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);


        if (this.keepAlive) {
            // Add keep alive header as per:
            // - http://www.w3.org/Protocols/HTTP/1.1/draft-ietf-http-v11-spec-01.html#Connection
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        }

        if (this.sendCookies) {
			// Encode the cookies
			for (Cookie c : this.cookies.values())
				response.headers().add(HttpHeaderNames.SET_COOKIE, ServerCookieEncoder.STRICT.encode(c));
		}
	
		RecordStruct headers = this.getFieldAsRecord("Headers");
	
		if (headers != null)
			for (FieldStruct h : headers.getFields())
				response.headers().set(h.getName(), Struct.objectToString(h.getValue()));
	
		// TODO restore - Hub.instance.getSecurityPolicy().hardenHttpResponse(response);
        
    	if (Logger.isDebug()) {
    		Logger.debug("Web server responding to " + ch.remoteAddress());
    		
    		for (Entry<String, String> ent : response.headers().entries()) {
        		Logger.debug("Response header: " + ent.getKey() + ": " + ent.getValue());
    		}
    	}
    	
        // Write the response.
        ChannelFuture future = ch.writeAndFlush(response);

        // Close the non-keep-alive connection after the write operation is done.
        if (!this.keepAlive) 
            future.addListener(ChannelFutureListener.CLOSE);
		else
			future.addListener(new ChannelFutureListener() {
				@Override
				public void operationComplete(ChannelFuture channelFuture) throws Exception {
					ch.read();
				}
			});
    }
    
    public void writeStart(Channel ch, long contentLength) {
		int code = (int) this.getFieldAsInteger("Code", 200);

		HttpResponseStatus status = HttpResponseStatus.valueOf(code);

		// Build the response object.
    	HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, status);

        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");

        if (this.keepAlive) {
            // Add 'Content-Length' header only for a keep-alive connection.
        	if (contentLength > 0)
        		response.headers().set(HttpHeaderNames.CONTENT_LENGTH, contentLength);
        	
            // Add keep alive header as per:
            // - http://www.w3.org/Protocols/HTTP/1.1/draft-ietf-http-v11-spec-01.html#Connection
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        }

        if (contentLength == 0)
            response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        
        // Encode the cookies
		if (this.sendCookies) {
			for (Cookie c : this.cookies.values())
				response.headers().add(HttpHeaderNames.SET_COOKIE, ServerCookieEncoder.STRICT.encode(c));
		}
	
		RecordStruct headers = this.getFieldAsRecord("Headers");
	
		if (headers != null)
			for (FieldStruct h : headers.getFields())
				response.headers().set(h.getName(), Struct.objectToString(h.getValue()));
	
		// TODO restore - Hub.instance.getSecurityPolicy().hardenHttpResponse(response);
        
    	if (Logger.isDebug()) {
    		Logger.debug("Web server responding to " + ch.remoteAddress());
    		
    		for (Entry<String, String> ent : response.headers().entries()) {
        		Logger.debug("Response header: " + ent.getKey() + ": " + ent.getValue());
    		}
    	}
        
        // Write the response.
        ch.writeAndFlush(response);
    }
    
    public void writeEnd(Channel ch) {
        // Write the response.
        ChannelFuture future = ch.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);

        // Close the non-keep-alive connection after the write operation is done.
        if (! this.keepAlive)
            future.addListener(ChannelFutureListener.CLOSE);
		else
			future.addListener(new ChannelFutureListener() {
				@Override
				public void operationComplete(ChannelFuture channelFuture) throws Exception {
					ch.read();
				}
			});
    }
    
    public void writeChunked(Channel ch) {
		int code = (int) this.getFieldAsInteger("Code", 200);

		HttpResponseStatus status = HttpResponseStatus.valueOf(code);

		// Build the response object.
    	HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, status);

        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");

        if (this.keepAlive) {
            // Add keep alive header as per:
            // - http://www.w3.org/Protocols/HTTP/1.1/draft-ietf-http-v11-spec-01.html#Connection
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        }
        
        // TODO add a customer header telling how many messages are in the session adaptor's queue - if > 0

        // Encode the cookies
		if (this.sendCookies) {
			for (Cookie c : this.cookies.values())
				response.headers().add(HttpHeaderNames.SET_COOKIE, ServerCookieEncoder.STRICT.encode(c));
		}

		RecordStruct headers = this.getFieldAsRecord("Headers");
	
		if (headers != null)
			for (FieldStruct h : headers.getFields())
				response.headers().set(h.getName(), Struct.objectToString(h.getValue()));

    	response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        
        // Write the response.
        ChannelFuture future = ch.writeAndFlush(response);

        // Close the non-keep-alive connection after the write operation is done.
        if (! this.keepAlive)
            future.addListener(ChannelFutureListener.CLOSE);
		else
			future.addListener(new ChannelFutureListener() {
				@Override
				public void operationComplete(ChannelFuture channelFuture) throws Exception {
					ch.read();
				}
			});
    }
    
    public void writeDownloadHeaders(Channel ch, String name, String mime) {
		int code = (int) this.getFieldAsInteger("Code", 200);

		HttpResponseStatus status = HttpResponseStatus.valueOf(code);

		// Build the response object.
    	HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, status);

        response.headers().set(HttpHeaderNames.CONTENT_TYPE, StringUtil.isNotEmpty(mime) ? mime :
				ResourceHub.getResources().getMime().getMimeTypeForName(name));
        
        if (StringUtil.isEmpty(name))
        	name = FileUtil.randomFilename("bin");
        
        response.headers().set("Content-Disposition", "attachment; filename=\"" + dcraft.util.net.NetUtil.urlEncodeUTF8(name) + "\"");
        
		Cookie dl = new DefaultCookie("fileDownload", "true");
		dl.setPath("/");
        
        // Encode the cookies
		if (this.sendCookies) {
			response.headers().add(HttpHeaderNames.SET_COOKIE, ServerCookieEncoder.STRICT.encode(dl));

			for (Cookie c : this.cookies.values())
				response.headers().add(HttpHeaderNames.SET_COOKIE, ServerCookieEncoder.STRICT.encode(c));
		}

		RecordStruct headers = this.getFieldAsRecord("Headers");
	
		if (headers != null)
			for (FieldStruct h : headers.getFields())
				response.headers().set(h.getName(), Struct.objectToString(h.getValue()));

    	response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        
        // Write the response.
        ch.writeAndFlush(response);
    }
    
	@SuppressWarnings("deprecation")
	public void load(ChannelHandlerContext ctx, HttpRequest req) {
		this.keepAlive = HttpHeaders.isKeepAlive(req);
	}
	
	public void loadVoid() {
	}

	// TODO don't like the body approach
	/*
	public void setBody(Message m) {
		// TODO make more efficient
		// TODO cleanup the content of the message some for browser?
		this.body.write(m.toString());
	}
	*/

	public void addBody(String v) {
		this.body.write(v);
	}

	public void setBody(Memory v) {
		this.body = v;
	}
	
	public void setBody(RecordStruct m) {
		// TODO make more efficient
		// TODO cleanup the content of the message some for browser?
		this.body.write(m.toString());
	}
	
	public Memory getBody() {
		return this.body;
	}
	
	@Override
	public ReturnOption operation(StackWork stack, XElement code) throws OperatingContextException {
		if ("DownloadHeaders".equals(code.getName())) {
			String name = StackUtil.stringFromElement(stack, code, "FileName");
			
			if (StringUtil.isEmpty(name))
				name = FileUtil.randomFilename("bin");
			
			String mime = StackUtil.stringFromElement(stack, code, "Mime");
			
			if (StringUtil.isEmpty(mime))
				mime = ResourceHub.getResources().getMime().getMimeTypeForName(name).getMimeType();
			
			this.setHeader("Content-Type", mime);
			this.setHeader("Content-Disposition", "attachment; filename=\"" + dcraft.util.net.NetUtil.urlEncodeUTF8(name) + "\"");
			
			return ReturnOption.CONTINUE;
		}
		
		if ("SetCookie".equals(code.getName())) {
			String name = StackUtil.stringFromElement(stack, code, "Name");
			String value = StackUtil.stringFromElement(stack, code, "Value");

			DefaultCookie cookie = new DefaultCookie(name, (value != null) ? value : "");
			cookie.setPath(StackUtil.stringFromElement(stack, code, "Path", "/"));
			cookie.setHttpOnly(StackUtil.boolFromElement(stack, code, "HttpOnly", true));

			// help pass security tests if Secure by default when using https
			cookie.setSecure(StackUtil.boolFromElement(stack, code, "Secure", true));

			cookie.setSameSite(CookieHeaderNames.SameSite.valueOf(StackUtil.stringFromElementClean(stack, code, "SameSite", "Lax")));

			Long maxAge = StackUtil.intFromElement(stack, code,"MaxAge");

			if (maxAge != null)
				cookie.setMaxAge(maxAge);

			setCookie(cookie);

			return ReturnOption.CONTINUE;
		}

		return super.operation(stack, code);
	}
}
