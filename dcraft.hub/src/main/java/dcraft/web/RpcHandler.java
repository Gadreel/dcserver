package dcraft.web;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.service.ServiceHub;
import dcraft.service.ServiceRequest;
import dcraft.session.Session;
import dcraft.session.SessionHub;
import dcraft.struct.CompositeParser;
import dcraft.struct.CompositeStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.Memory;
import dcraft.util.StringUtil;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class RpcHandler implements IContentDecoder {
	static public RpcHandler create(OperationContext opctx) {
		RpcHandler handler = new RpcHandler();
		
		handler.opcontext.set(opctx);
		
		return handler;
	}
	
	protected AtomicReference<OperationContext> opcontext = new AtomicReference<>();		// TODO this probably isn't needed, remove

	protected Memory mem = new Memory();
	
	public void updateOpContext(OperationContext opcontext) {
		this.opcontext.set(opcontext);
	}
	
	protected RpcHandler() {
	}
	
	// TODO allow up to 48kb? 64kb?
	
	@Override
	public void offer(HttpContent chunk) {
		OperationContext.set(this.opcontext.get());
		
		int newsize = chunk.content().readableBytes() + mem.getLength();
		
		if (newsize > 4 * 1024 * 1024) {
			this.fail();
			return;
		}
		
		for (ByteBuffer b : chunk.content().nioBuffers())
			mem.write(b);
		
		if (chunk instanceof LastHttpContent)  {
			try {
				this.process();
			}
			catch (OperatingContextException x) {
				this.fail();
			}
		}
		else {
			WebController wctrl = (WebController) this.opcontext.get().getController();

			wctrl.getChannel().read();
		}
	}
	
	@Override
	public void release() {
		OperationContext.set(this.opcontext.get());
		
		this.fail();
	}
	
	@Override
	public String toString() {
		return this.mem.toString();
	}
	
	public void fail() {
		Logger.error("RPC Message failed");
		
		// sort of cleanup references
		OperationContext ctx = this.opcontext.getAndSet(null);
		
		if (ctx == null)
			return;
		
		WebController wctrl = (WebController) ctx.getController();
		
		wctrl.sendRequestBad();
		
		wctrl.setDecoder(null);
	}

	public void process() throws OperatingContextException {
		if (Logger.isTrace())
			Logger.trace("RPC Message collected");
		
		// sort of cleanup references
		OperationContext ctx = this.opcontext.getAndSet(null);
		
		if (ctx == null)
			return;

		OperationContext.set(ctx);
		
		WebController wctrl = (WebController) ctx.getController();
		
		wctrl.setDecoder(null);
		
		CompositeStruct croot = CompositeParser.parseJson(this.mem);
		
		if ((croot == null) || ! (croot instanceof RecordStruct)) {
			wctrl.sendRequestBad();
			return;
		}
		
		RecordStruct msg = (RecordStruct) croot;
		
		// check that the request conforms to the schema for RpcMessage
		if (! msg.validate("RpcMessage")) {
			wctrl.sendRequestBad();
			return;
		}
		
		//System.out.println("got rpc message: " + msg);
		
		/* TODO review
		String sessionid = msg.getFieldAsString("Session");
		
		msg.removeField("Session");
		
		String dlevel = msg.getFieldAsString("DebugLevel");
		
		// allow people to change debug level if debug is enabled
		if (StringUtil.isNotEmpty(dlevel) && HubLog.getDebugEnabled()) {
			msg.removeField("DebugLevel");
			OperationContext.get().setLevel(DebugLevel.parse(dlevel));
		}
		*/
		
		if (Logger.isDebug())
			Logger.debug("RPC Message: " + msg.getFieldAsString("Service") + " - " + msg.getFieldAsString("Feature")
					+ " - " + msg.getFieldAsString("Op"));
		
		ServiceRequest request = ServiceRequest.of(msg.getFieldAsString("Service"), msg.getFieldAsString("Feature"), msg.getFieldAsString("Op"))
				.withData(msg.getField("Body"))
				.withFromRpc();
		
		// for SendForget don't wait for a callback, just return success
		if ("SendForget".equals(msg.getFieldAsString("RespondTag"))) {
			// send to bus
			ServiceHub.call(request);
			
			RecordStruct rmsg = RecordStruct.record()
					.with("Tag", msg.getFieldAsString("RespondTag"))
					.with("Messages", wctrl.getMessages());
			
			/*
			Session sess = ctx.getSession();
			
			String currsessid = sess.getId();
			
			rmsg.setField("Session", currsessid);
			
			// TODO review - this will really be about tokens not sessions
			if ((sessionid != null) && !currsessid.equals(sessionid))
				rmsg.setField("SessionChanged", true);
			*/
			
			// TODO pickup from mailbox
			
			// reply to client, don't wait for response
			
			wctrl.sendMessage(rmsg);

			return;
		}
		
		String oldtoken = ctx.getUserContext().getAuthToken();
		
		// don't use local vars in this callback
		request.withOutcome(new OperationOutcomeStruct() {
			@Override
			public void callback(Struct result) throws OperatingContextException {
				OperationContext ctx = OperationContext.getOrThrow();
				
				WebController wctrl = (WebController) ctx.getController();
				
				RecordStruct rmsg = RecordStruct.record()
						.with("Messages", wctrl.getMessages())
						.with("Tag", msg.getFieldAsString("RespondTag"))
						.with("Body", result);
				
				// TODO consider session changed issues above, here also
				
				Session sess = ctx.getSession();
				
				// session may be null on Session - Control - Stop
				if (sess != null) {
					/*
					String currsessid = sess.getId();
					
					rmsg.setField("Session", currsessid);
					
					if ((this.sessid != null) && !currsessid.equals(this.sessid))
						rmsg.setField("SessionChanged", true);
					*/
					// web server does not send SessionSecret or AuthToken in response
					
					//System.out.println("outgoing rpc: " + rmsg);
					
					String authupdate = ctx.getUserContext().getAuthToken();
					
					if (! Objects.equals(oldtoken, authupdate)) {
						Cookie sk = new DefaultCookie("dcAuthToken", authupdate);
						sk.setPath("/");
						sk.setHttpOnly(true);
						
						wctrl.getResponse().setCookie(sk);
					}
				}
				
				wctrl.sendMessage(rmsg);
			}
		});
		
		ServiceHub.call(request);
		
		// make sure we don't use this in inner classes (by making it non-final)
		ctx = null;
	}
}
