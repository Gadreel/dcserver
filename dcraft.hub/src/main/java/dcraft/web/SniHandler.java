package dcraft.web;

import dcraft.hub.ResourceHub;
import dcraft.hub.resource.TrustResource;
import dcraft.log.Logger;
import dcraft.tenant.Site;
import dcraft.tenant.TenantHub;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ssl.*;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.AsyncMapping;
import io.netty.util.DomainNameMapping;
import io.netty.util.DomainNameMappingBuilder;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ProgressivePromise;

import java.net.IDN;
import java.util.Locale;

import static io.netty.handler.codec.http2.Http2SecurityUtil.CIPHERS;

/**
 */
public class SniHandler extends io.netty.handler.ssl.SniHandler {
	// this does nothing at all, but super class wants an instance so here it is :(
	static protected DomainNameMapping<SslContext> defaultmapper = null;
	
	static public SniHandler sni() {
		TrustResource tr = ResourceHub.getResources().getTrust();
		
		try {
			if (SniHandler.defaultmapper == null) {
				SslContext scontext = tr.lookupSsl("root").getServerBuilder(tr).build();
				
				SniHandler.defaultmapper = new DomainNameMappingBuilder<>(scontext).build();
			}
			
			return new SniHandler(SniHandler.defaultmapper);
		}
		catch (Exception x) {
			Logger.error("Unable to create context for SniHandler");
			return null;
		}
	}
	
	public SniHandler(DomainNameMapping<SslContext> mapper) {
		super(mapper);
	}
	
	@Override
	protected Future<SslContext> lookup(ChannelHandlerContext ctx, String hostname) throws Exception {
		ProgressivePromise<SslContext> sfuture = ctx.executor().newProgressivePromise();
		
		if (hostname == null)
			hostname = "localhost";
		// TODO need this?
		// else
		//	hostname = IDN.toASCII(hostname, IDN.ALLOW_UNASSIGNED).toLowerCase(Locale.US);
		
		Site site = TenantHub.resolveSite(hostname);
		
		if (site != null) {
			TrustResource tr = site.getResources().getTrust();
			
			try {
				SslContext scontext = tr.lookupSsl(hostname).getServerBuilder(tr)
						.build();
				
				sfuture.setSuccess(scontext);
			}
			catch (Exception x) {
				Logger.error("Unable to create context within SniHandler");
				sfuture.setFailure(x);
			}
		}
		else {
			sfuture.setFailure(new Exception("Unable to resolve site"));
		}
		
		return sfuture;
	}

	@Override
	protected void replaceHandler(ChannelHandlerContext ctx, String hostname, SslContext sslContext) throws Exception {
		SslHandler sslHandler = null;
		
		try {
			sslHandler = sslContext.newHandler(ctx.alloc());
			ctx.pipeline().replace(this, "ssl", sslHandler);
			sslHandler = null;
		} finally {
			if (sslHandler != null) {
				ReferenceCountUtil.safeRelease(sslHandler.engine());
			}
			
		}
		
	}
}
