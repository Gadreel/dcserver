package dcraft.web;

import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.app.HubEvents;
import dcraft.hub.app.HubState;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.resource.ResourceTier;
import dcraft.log.Logger;
import dcraft.service.BaseService;
import dcraft.service.ServiceRequest;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 */
public class Service extends BaseService {
	protected ConcurrentHashMap<Integer, Channel> activelisteners = new ConcurrentHashMap<>();
	protected ReentrantLock listenlock = new ReentrantLock();
	
	protected String defaultTlsPort = "443";
	
	@Override
	public void start() {
		// TODO fix so wait for server running event or after tenant reload event to do the binding
		XElement settings = this.config.find("Settings");
		
		if (settings != null) {
			this.defaultTlsPort = settings.getAttribute("DefaultSecurePort", this.defaultTlsPort);

			if (settings.hasNotEmptyAttribute("Protocols"))
				ResourceHub.getTopResources().getTrust().withProtocols(settings.getAttribute("Protocols").split(","));

			if (settings.hasNotEmptyAttribute("Ciphers"))
				ResourceHub.getTopResources().getTrust().withCiphers(settings.getAttribute("Ciphers").split(","));
		}

		// TODO review why there are two separate subscriptions...
		ApplicationHub.subscribeToEvents((e, data) -> {
			if ((e == HubEvents.HubState) && (data == HubState.Running))
				this.goOnline();
			else if (e == HubEvents.TenantsReloaded)
				this.goOnline();
		});
		
		ApplicationHub.subscribeToEvents((e, data) -> {
			if ((e == HubEvents.HubState) && (data == HubState.Idle))
				this.goOffline();
			else if ((e == HubEvents.HubState) && (data == HubState.Stopping))
				this.goOffline();
		});

		super.start();
	}
	
	@Override
	public void stop() {
		this.goOffline();
		
		super.stop();
	}
	
	public void goOnline() {
		XElement settings = this.config.find("Settings");
		
		if (settings == null)
			return;
		
		this.listenlock.lock();
		
		try {
			// don't try if already in online mode
			if (this.activelisteners.size() > 0)
				return;
			
			// typically we should have an extension, unless we are supporting RPC only
			// TODO if (WebSiteManager.instance.getDefaultExtension() == null)
			//	log.warn(0, "No default extension for web server");
			//boolean deflate = "True".equals(this.config.getAttribute("Deflate"));
			
			for (XElement httpconfig : settings.selectAll("HttpListener")) {
				boolean secure = httpconfig.getAttributeAsBooleanOrFalse("Secure");
				int httpport = (int) StringUtil.parseInt(httpconfig.getAttribute("Port"), secure ? 443 : 80);
				
				
				// -------------------------------------------------
				// message port
				// -------------------------------------------------
				ServerBootstrap b = new ServerBootstrap();
				
				b.group(ApplicationHub.getEventLoopGroup())
						.channel(ApplicationHub.getServerSocketChannel())
						.option(ChannelOption.ALLOCATOR, ApplicationHub.getBufferAllocator())
						.option(ChannelOption.SO_BACKLOG, 1024)
						//.option(ChannelOption.TCP_NODELAY, true)
						//.option(ChannelOption.AUTO_READ, false)
						.childHandler(new ChannelInitializer<SocketChannel>() {
							@Override
							protected void initChannel(SocketChannel ch) throws Exception {
								if (Logger.isTrace())
									Logger.trace("Start web connection from " + ch.remoteAddress().getAddress().toString());

								ChannelPipeline pipeline = ch.pipeline();
								
								pipeline.addLast("timeout", new ReadTimeoutHandler(600));   // solves notebook sleep issue.  after 10 minutes close socket
								
								if (secure) {
									if (Logger.isTrace())
										Logger.trace("HTTPS Connection");

									pipeline.addLast("ssl", SniHandler.sni());

									pipeline.addLast("router", new Http2OrHttpHandler(settings));
								}
								else {
									if (Logger.isTrace())
										Logger.trace("HTTP Connection");

									Http2OrHttpHandler.configureHttp1(pipeline, settings);
								}

								/*
								pipeline.addLast("decoder", new HttpRequestDecoder(4096,8192,262144));
								pipeline.addLast("encoder", new HttpResponseEncoder());
								
								//if (deflate)
								//	pipeline.addLast("deflater", new HttpContentCompressor());
								
								pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());
								
								pipeline.addLast("handler", new ServerHandler());
								*/
								
								if (Logger.isDebug())
									Logger.debug("New web connection from " + ch.remoteAddress().getAddress().toString());
							}
						});
				
				if (Logger.isDebug())
					b.handler(new LoggingHandler("www", Logger.isTrace() ? LogLevel.TRACE : LogLevel.DEBUG));
				
				try {
					// must wait here, both to keep the activelisteners listeners up to date
					// but also to make sure we don't release connectLock too soon
					ChannelFuture bfuture = b.bind(httpport).sync();
					
					if (bfuture.isSuccess()) {
						Logger.info("Web Server listening - now listening for HTTP on TCP port " + httpport);
						this.activelisteners.put(httpport, bfuture.channel());
					}
					else
						Logger.error("Web Server unable to bind: " + bfuture.cause());
				}
				catch (InterruptedException x) {
					Logger.error("Web Server interrupted while binding: " + x);
				}
				catch (Exception x) {
					Logger.error("Web Server errored while binding: " + x);
				}
			}
		}
		finally {
			this.listenlock.unlock();
		}
	}
	
	public void goOffline() {
		this.listenlock.lock();
		
		try {
			// we don't want to listen anymore
			for (Integer port : this.activelisteners.keySet()) {
				// tear down message port
				Channel ch = this.activelisteners.remove(port);
				
				try {
					// must wait here, both to keep the activelisteners listeners up to date
					// but also to make sure we don't release connectLock too soon
					ChannelFuture bfuture = ch.close().sync();
					
					if (bfuture.isSuccess())
						Logger.info("Web Server unbound");
					else
						Logger.error("Web Server unable to unbind: " + bfuture.cause());
				}
				catch (InterruptedException x) {
					Logger.error("Web Server unable to unbind: " + x);
				}
			}
		}
		finally {
			this.listenlock.unlock();
		}
	}
	
	@Override
	public boolean handle(ServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException {
		return false;
	}
}
