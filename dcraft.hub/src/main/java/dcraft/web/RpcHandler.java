package dcraft.web;

import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.resource.KeyRingResource;
import dcraft.log.Logger;
import dcraft.service.ServiceHub;
import dcraft.service.ServiceRequest;
import dcraft.session.Session;
import dcraft.session.SessionHub;
import dcraft.struct.*;
import dcraft.struct.scalar.StringStruct;
import dcraft.tenant.Tenant;
import dcraft.tenant.TenantHub;
import dcraft.util.Memory;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.util.pgp.ClearsignUtil;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.CookieHeaderNames;
import io.netty.handler.codec.http.cookie.DefaultCookie;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
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
		
		wctrl.sendRequestBadRead();
		
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

		this.mem.setPosition(0);

		CompositeStruct croot = null;

		// TODO check for signed message - note cannot handle encrypted (yet)

		String signkey = wctrl.selectAsString("Request.Headers.X-Dc-Sign-Key");

		if (StringUtil.isNotEmpty(signkey)) {
			// signkey is not enforced, just informational
			KeyRingResource keys = ResourceHub.getResources().getKeyRing();

			StringBuilder sb = new StringBuilder();
			StringStruct sig = StringStruct.ofEmpty();
			StringStruct key = StringStruct.ofEmpty();

			try (InputStream bais = this.mem.getInputStream()) {
				ClearsignUtil.verifyFile(bais, keys, sb, sig, key);
			}
			catch (IOException x) {
				// na
			}

			if (sig.isEmpty()) {
				//System.out.println("failed");
				Logger.warn("Signed message failed to verify");
			}
			else {
				//System.out.println("success, key: " + key);
				Logger.info("Got signed message: " + sb);

				RecordStruct message = Struct.objectToRecord(CompositeParser.parseJson(sb));

				if (message != null) {
					String dest = message.getFieldAsString("Destination");

					if (StringUtil.isNotEmpty(dest)) {
						ZonedDateTime expires = message.getFieldAsDateTime("Expires");

						if ((expires != null) && expires.isAfter(TimeUtil.now())) {
							int dpos = dest.indexOf('/');

							if (dpos > 0) {
								String deployment = dest.substring(0, dpos);
								String tenantalias = dest.substring(dpos + 1);

								if (ApplicationHub.getDeployment().equals(deployment)) {
									Tenant tenant = TenantHub.resolveTenant(tenantalias);

									if (tenant != null) {
										croot = message.getFieldAsRecord("Payload");
									}
									else {
										Logger.warn("Signed message is for a missing tenant");
									}
								}
								else {
									Logger.warn("Signed message is for a different deployment");
								}
							}
						}
						else {
							Logger.warn("Signed message is expired or missing Expires field");
						}
					}
					else {
						Logger.warn("Signed message is missing Destination field");
					}
				}
				else {
					Logger.warn("Unable to parse signed message");
				}
			}
		}
		else {
			croot = CompositeParser.parseJson(this.mem);
		}

		if ((croot == null) || ! (croot instanceof RecordStruct)) {
			wctrl.sendRequestBadRead();
			return;
		}
		
		RecordStruct msg = (RecordStruct) croot;

		// convert from Op only to three parts, if possible
		if (msg.isFieldEmpty("Service") && ! msg.isFieldEmpty("Op")) {
			ServiceRequest request = ServiceRequest.of(msg.getFieldAsString("Op"));

			msg.with("Service", request.getName());
			msg.with("Feature", request.getFeature());
			msg.with("Op", request.getOp());
		}
		
		// check that the request conforms to the schema for RpcMessage
		if (! msg.validate("RpcMessage")) {
			wctrl.sendRequestBadRead();
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
				.withAsIncomplete()		// service doesn't have to be final data, that can be a separate app logic check
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
			public void callback(BaseStruct result) throws OperatingContextException {
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
						DefaultCookie sk = new DefaultCookie("dcAuthToken", authupdate);
						sk.setPath("/");
						sk.setHttpOnly(true);

						sk.setSameSite(CookieHeaderNames.SameSite.None);
						// help pass security tests if Secure by default when using https
						sk.setSecure(wctrl.isSecure());
						
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
