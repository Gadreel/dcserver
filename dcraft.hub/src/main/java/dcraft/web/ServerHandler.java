package dcraft.web;

import dcraft.filestore.CommonPath;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.app.HubState;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationMarker;
import dcraft.hub.op.UserContext;
import dcraft.locale.LocaleDefinition;
import dcraft.locale.LocaleResource;
import dcraft.locale.LocaleUtil;
import dcraft.log.Logger;
import dcraft.log.count.CountHub;
import dcraft.session.Session;
import dcraft.session.SessionHub;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.Task;
import dcraft.task.TaskHub;
import dcraft.tenant.Site;
import dcraft.tenant.TenantHub;
import dcraft.util.StringUtil;
import dcraft.util.net.IpAddress;
import dcraft.util.net.NetUtil;
import dcraft.xml.XElement;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.ReadTimeoutException;

import java.net.InetSocketAddress;

/**
 */
public class ServerHandler extends SimpleChannelInboundHandler<Object> {
	static protected final String DYN_PATH = "dcdyn";
	static protected final String RPC_PATH = "rpc";
	static protected final String STATUS_PATH = "status";
	static protected final String TRANSFER_PATH = "xfer";
	static protected final String VAULT_PATH = "vault";

	protected OperationContext context = null;
	protected XElement servicesettings = null;		// TODO load these from service
	
	// TODO improve to ignore large PUTs on most Paths
	// TODO ip lockout
	// TODO acl
	// TODO debug level based in ip address
	// TODO any where along the way, especially RPC, ping Remote Trust Center with down votes if doesn't work out
	
	@Override
	protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
		if (Logger.isTrace())
			Logger.trace("HTTP Message: " + msg);

		if (msg instanceof HttpContent) {
			if (this.context == null) {
				Logger.error("Got more content but no context");
				ctx.channel().read();
				return;
			}
			
			OperationContext.set(this.context);
			
			Session session = this.context.getSession();
			
			if (session == null) {
				Logger.error("Got more content but no session - could be a redirect");
				ctx.channel().read();
				return;
			}
			
			if (Logger.isDebug())
				Logger.debug("Web server request rrr");
			
			session.touch();
			
			((WebController) this.context.getController()).offerContent((HttpContent)msg);
			
			if (msg instanceof LastHttpContent)
				this.context = null;
			
			return;
		}
		
		// each new request starts with Guest
		WebController wctrl = WebController.forChannel(ctx.channel(), servicesettings);
		
		OperationContext wctx = this.context = OperationContext.context(UserContext.guestUser(), wctrl);
		
		// erase any lingering context
		OperationContext.set(wctx);
		
		OperationMarker om = OperationMarker.create();
		
		if (Logger.isDebug())
			Logger.debug("Web server request " + msg.getClass().getName() + "  " + ctx.channel().localAddress()
					+ " from " + ctx.channel().remoteAddress());
		
		if (! (msg instanceof HttpRequest)) {
			wctrl.sendRequestBadRead();
			return;
		}

		ctx.channel().config().setAutoRead(false);

		HttpRequest httpreq = (HttpRequest) msg;
		
		// at the least don't allow web requests until running
		// TODO later we may need to have a "Going Down" flag and filter new requests but allow existing
		if (! ApplicationHub.isRunning()) {
			wctrl.sendInternalErrorRead();
			return;
		}

		if (Logger.isDebug())
			Logger.debug("Web server request a");

		// Handle a bad request.
		if (! httpreq.decoderResult().isSuccess()) {
			wctrl.sendRequestBadRead();
			return;
		}

		if (Logger.isDebug())
			Logger.debug("Web server request b");

		wctrl.initWeb(ctx, httpreq);

		if (Logger.isDebug())
			Logger.debug("Web server request c");

		RecordStruct req = wctrl.getFieldAsRecord("Request");
		
		CommonPath path = CommonPath.from(req.getFieldAsString("Path"));
		String method = req.getFieldAsString("Method");
		String host = req.getFieldAsString("Host");
		
		boolean pass = "GET".equals(method) || "POST".equals(method);

		if (Logger.isDebug())
			Logger.debug("Web server request d");

		// very limited support for http method - on purpose
		if ("PUT".equals(method) && (path.getNameCount() > 0) && path.getName(0).equals(ServerHandler.DYN_PATH))
			pass = true;
		
		if (! pass) {
			wctrl.sendRequestBadRead();
			return;
		}
		
		String origin = "http:" + NetUtil.formatIpAddress((InetSocketAddress)ctx.channel().remoteAddress());
		
		wctx.withOrigin(origin);
		
		// TODO use X-Forwarded-For  if available, maybe a plug in approach to getting client's IP?
		
		String dname = host;
		
		if (StringUtil.isNotEmpty(dname)) {
			int cpos = dname.indexOf(':');
			
			if (cpos > -1)
				dname = dname.substring(0, cpos);
		}
		
		if (StringUtil.isEmpty(dname) || IpAddress.isIpLiteral(dname))
			dname = "localhost";

		if (Logger.isDebug())
			Logger.debug("Web server request e");

		Site site = TenantHub.resolveSite(dname);
		
		if (site == null) {
			if (Logger.isDebug())
				Logger.debug("Tenant not found for: " + host);
			
			wctrl.sendForbiddenRead();
			return;
		}
		
		wctx.getUserContext()
				.withTenantAlias(site.getTenant().getAlias())
				.withSiteAlias(site.getAlias());
		
		// --------------------------------------------
		// status request
		// --------------------------------------------
		
		if ((path.getNameCount() == 2) && path.getName(0).equals(ServerHandler.DYN_PATH)
				&& path.getName(1).equals(ServerHandler.STATUS_PATH)) {
			CountHub.countObjects("dcWebStatusCount-" + site.getTenant().getAlias(), req);
			
			if (ApplicationHub.getState() == HubState.Running)
				wctrl.sendRequestOkRead();
			else
				wctrl.sendRequestBadRead();
			
			return;
		}
		
		// --------------------------------------------
		// audit request
		// --------------------------------------------
		
		CountHub.countObjects("dcWebRequestCount-" + site.getTenant().getAlias(), req);
		
		Logger.info("Web request for host: " + host +  " url: " + path + " by: " +
				origin + " in: " + site.getTenant().getAlias() + "/" + site.getAlias());
		
		// --------------------------------------------
		// reroute request
		// --------------------------------------------
		
		// check into url re-routing - before context/session!
		String reroute = site.webRoute(wctrl, (SslHandler)ctx.channel().pipeline().get("ssl"));
		
		if (StringUtil.isNotEmpty(reroute)) {
			if (Logger.isDebug())
				Logger.debug("Routing the request to: " + reroute);
			
			wctrl.getResponse().setStatus(HttpResponseStatus.MOVED_PERMANENTLY);
			wctrl.getResponse().setHeader(HttpHeaderNames.LOCATION.toString(), reroute);
			wctrl.sendRead();
			return;
		}

		if (Logger.isDebug())
			Logger.debug("Web server request f");

		// --------------------------------------------
		// work for request
		// --------------------------------------------
		
		RequestWork rwork = new RequestWork();
		
		// --------------------------------------------
		// session for request
		// --------------------------------------------
		
		Session sess = null;
		
		String sesscookie = req.isNotFieldEmpty("Cookies") ? req.getFieldAsRecord("Cookies").getFieldAsString("dcSessionId") : null;
		String catoken = req.isNotFieldEmpty("Cookies") ? req.getFieldAsRecord("Cookies").getFieldAsString("dcAuthToken") : null;

		String authtoken = StringUtil.isNotEmpty(catoken) ? catoken : null;
		
		rwork.setNeedsVerify(StringUtil.isNotEmpty(authtoken));
		
		String fauthtoken = rwork.getNeedsVerify() ? authtoken : null;		// make sure this is null not empty

		if (Logger.isDebug())
			Logger.debug("Web server request f1");

		if (sesscookie != null) {
			int upos = sesscookie.lastIndexOf('_');
			
			if (upos != -1) {
				String sessionid = sesscookie.substring(0, upos);
				String accesscode = sesscookie.substring(upos + 1);
				
				sess = SessionHub.lookup(sessionid);
				
				if (sess != null) {
					if (! sess.getKey().equals(accesscode) || ! sess.isKnownAuthToken(fauthtoken)) {
						// TODO sess is there but wrong key - major, 100%, trust issue!
						Cookie sessk = new DefaultCookie("dcSessionId", "");
						sessk.setPath("/");
						sessk.setHttpOnly(true);
						
						// help pass security tests if Secure by default when using https
						sessk.setSecure(wctrl.isSecure());
						
						wctrl.getResponse().setCookie(sessk);
						
						// TODO sess is there, authtoken is there, but wrong token - major, 100%, trust issue!
						Cookie authk = new DefaultCookie("dcAuthToken", "");
						authk.setPath("/");
						authk.setHttpOnly(true);
						
						// help pass security tests if Secure by default when using https
						authk.setSecure(wctrl.isSecure());
						
						wctrl.getResponse().setCookie(authk);
						
						wctrl.getResponse().setStatus(HttpResponseStatus.FOUND);
						wctrl.getResponse().setHeader(HttpHeaderNames.LOCATION.toString(), "/");
						wctrl.getResponse().setHeader("Cache-Control", "no-cache");		// in case they login later, firefox was using cache
						wctrl.sendRead();
						return;
					}
					
					wctx.with("User", sess.getUser());
					
					// TODO check that we also get the proper settings from Session - like debuglevel
					
					rwork.setNeedsVerify(false);
				}
			}
		}

		if (Logger.isDebug())
			Logger.debug("Web server request f2");

		rwork.setOldAuthToken(fauthtoken);
		
		if (sess == null) {
			sess = Session.of(origin, wctx.getUserContext());
			
			sess.getUser().withAuthToken(authtoken);

			if (Logger.isDebug())
				Logger.debug("Web server request f3");

			SessionHub.register(sess);
			
			Logger.info("Started new session: " + sess.getId() + " on " + path + " for " + origin + " agent: " + req.getFieldAsRecord("Headers").getFieldAsString("User-Agent"));
			
			// TODO if ssl set client key on user context
			//req.getSecuritySession().getPeerCertificates();
			
			/* TODO adapter review
			sess.setAdatper(new HttpAdapter(wctx));
			*/
			
			Cookie sk = new DefaultCookie("dcSessionId", sess.getId() + "_" + sess.getKey());
			sk.setPath("/");
			sk.setHttpOnly(true);
			
			// help pass security tests if Secure by default when using https
			sk.setSecure(wctrl.isSecure());
			
			wctrl.getResponse().setCookie(sk);

			if (Logger.isDebug())
				Logger.debug("Web server request f4");
		}
		
		wctx.setSessionId(sess.getId());
		
		// --------------------------------------------
		// locale for request
		// --------------------------------------------
		
		{
			LocaleResource locales = this.context.getResources().getLocale();
			LocaleDefinition locale = null;
			boolean needredirect = false;
			
			// see if the path indicates a language then redirect
			if (path.getNameCount() > 0)  {
				String lvalue = LocaleUtil.normalizeCode(path.getName(0));
				
				locale = locales.getLocaleDefinition(lvalue);
				
				if (locale != null) {
					needredirect = true;

					req
							.with("Path", path.subpath(1))
							.with("OriginalPath", path.subpath(1));
				}
			}

			// if not changing due to Path, check change due to params
			if (! needredirect) {
				String lvalue = req.selectAsString("Parameters._dclang.0");

				if (StringUtil.isNotEmpty(lvalue))
					locale = locales.getLocaleDefinition(lvalue);
			}

			String langcookie = req.isNotFieldEmpty("Cookies")
					? req.getFieldAsRecord("Cookies").getFieldAsString("dcLang")
					: null;
			
			// but respect the cookie if possible
			if (locale == null) {
				if (StringUtil.isNotEmpty(langcookie)) {
					locale = locales.getLocaleDefinition(langcookie);
				}
			}
			
			// see if the domain is set for a specific language
			if (locale == null) {
				String domain = host;
				
				if (domain.indexOf(':') > -1)
					domain = domain.substring(0, domain.indexOf(':'));
				
				locale = this.context.getSite().getLocaleDomain(domain);
			}
			
			// see if the user has a preference
			if (locale == null) {
				ListStruct uplist = this.context.getUserContext().getLocale();
				
				if ((uplist != null) && (uplist.getSize() > 0))
					locale = locales.getLocaleDefinition(uplist.getItemAsString(0));		// TODO support more than just first preference
			}
			
			// use default locale for site
			if (locale == null) {
				locale = locales.getDefaultLocaleDefinition();
			}
			
			// if selected locale does not match context locale, switch
			if (! locale.getName().equals(this.context.getLocale())) {
				this.context.withLocale(locale.getName());
			}
			
			if (! locale.getName().equals(langcookie)) {
				Cookie localek = new DefaultCookie("dcLang", locale.getName());
				localek.setPath("/");

				// help pass security tests if Secure by default when using https
				localek.setSecure(wctrl.isSecure());

				wctrl.getResponse().setCookie(localek);
			}

			if (needredirect) {
				wctrl.getResponse().setStatus(HttpResponseStatus.FOUND);	// not permanent
				// TODO restore the other parameters too
				wctrl.getResponse().setHeader(HttpHeaderNames.LOCATION.toString(), req.getFieldAsString("OriginalPath") + "?_dclang=" + locale.getName());
				wctrl.getResponse().setHeader("Cache-Control", "no-cache");		// in case they login later, FireFox was using cache
				wctrl.sendRead();
				return;
			}
		}

		if (Logger.isDebug())
			Logger.debug("Web server request f5");

		// --------------------------------------------
		// Unless an DYN, all unverified tokens must be GET requests
		// --------------------------------------------
		
		// upload and download operations should occur only on a valid and active session that does not need a verify
		// thus they (well upload anyway) may fail if coming through here and somehow needed to verify
		if (rwork.getNeedsVerify() && ! "GET".equals(method)) {
			if ((path.getNameCount() < 2) || ! path.getName(0).equals(ServerHandler.DYN_PATH)) {
				wctrl.sendRequestBadRead();
				return;
			}
		}

		if (Logger.isDebug())
			Logger.debug("Web server request f6");

		// --------------------------------------------
		// check request status
		// --------------------------------------------
		
		// check errors now because we will clear errors after calling verify
		// the call to verify does not count as real errors in our own operations of loading pages
		if (om.hasErrors()) {
			wctrl.sendRequestBadRead();
			return;
		}
		
		//System.out.println("=========================== NOVHOATS: " + needVerify);
		
		// --------------------------------------------
		// submit the work work
		// --------------------------------------------

		if (Logger.isDebug())
			Logger.debug("Web server request g");

		TaskHub.submit(Task.ofSubtask("Process web request", "WEB")
				.withTopic("Web")
				.withWork(rwork)
		);
	}
	
	// TODO this may not be a real threat but review it anyway
	// http://www.christian-schneider.net/CrossSiteWebSocketHijacking.html
	
	// https://www.owasp.org/index.php/HTML5_Security_Cheat_Sheet
	// https://www.owasp.org/index.php/Cross_Site_Scripting_Flaw
	// https://www.owasp.org/index.php/XSS_(Cross_Site_Scripting)_Prevention_Cheat_Sheet
	// https://code.google.com/p/owasp-java-encoder/source/browse/trunk/core/src/main/java/org/owasp/encoder/HTMLEncoder.java
	// http://kefirsf.org/kefirbb/
	// http://codex.wordpress.org/Validating_Sanitizing_and_Escaping_User_Data
	// http://excess-xss.com/
	// http://en.wikipedia.org/wiki/HTTP_cookie
	
	//  If you wish to support both HTTP requests and websockets in the one server, refer to the io.netty.example.http.websocketx.server.WebSocketServer example. To know once a handshake was done you can intercept the ChannelInboundHandler.userEventTriggered(ChannelHandlerContext, Object) and check if the event was of type WebSocketServerProtocolHandler.ServerHandshakeStateEvent.HANDSHAKE_COMPLETE.
	
	// TODO CORS
	// also review
	// https://github.com/netty/netty/pull/2427/files
	// http://www.html5rocks.com/en/tutorials/file/xhr2/
	// http://www.html5rocks.com/en/tutorials/cors/
	// http://enable-cors.org/server.html
	
	// BREACH etc
	// https://community.qualys.com/blogs/securitylabs/2013/08/07/defending-against-the-breach-attack
	// https://en.wikipedia.org/wiki/BREACH_(security_exploit)
    
    /*
GET http://229097002.log.optimizely.com/event?a=229097002&d=229097002&y=false&x761570292=750582396&s231842852=gc&s231947722=search&s232031415=false&n=http%3A%2F%2Fwww.telerik.com%2Fdownload%2Ffiddler%2Ffirst-run&u=oeu1393506471224r0.17277055932208896&wxhr=true&t=1398696975163&f=702401691,760731745,761570292,766240693,834650096 HTTP/1.1
Host: 229097002.log.optimizely.com
Connection: keep-alive
User-Agent: Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/34.0.1847.131 Safari/537.36
Origin: http://www.telerik.com
Accept: * /*
Referer: http://www.telerik.com/download/fiddler/first-run
Accept-Encoding: gzip,deflate,sdch
Accept-Language: en-US,en;q=0.8
Cookie: fixed_external_20728634_bucket_map=; fixed_external_9718688_bucket_map=; fixed_external_138031368_bucket_map=; end_user_id=oeu1393506471224r0.17277055932208896; bucket_map=761570292%3A750582396



HTTP/1.1 200 OK
Access-Control-Allow-Credentials: true
Access-Control-Allow-Methods: POST, GET
Access-Control-Allow-Origin: http://www.telerik.com
Content-Type: application/json
Date: Mon, 28 Apr 2014 14:56:18 GMT
P3P: CP="IDC DSP COR CURa ADMa OUR IND PHY ONL COM STA"
Server: nginx/1.2.7
Content-Length: 2
Connection: keep-alive

{}



Chrome Web Socket Request:

GET /rpc HTTP/1.1
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Key: v8MIKFOPlaVtRK2C1iOJ4Q==
Host: localhost:9443
Sec-WebSocket-Origin: http://localhost:9443
Sec-WebSocket-Version: 13
x-DivConq-Mode: Private


Java API with Session Id

POST /rpc HTTP/1.1
Host: localhost
User-Agent: DivConq HyperAPI Client 1.0
Connection: keep-alive
Content-Encoding: UTF-8
Content-Type: application/json; charset=utf-8
Cookie: SessionId=00700_fa2h199tkc2e8i2cs4e8s9ujhh_EetvVV9EocXc; $Path="/"





	 *
     */
	
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		if (cause instanceof ReadTimeoutException) {
			Logger.info("Web server closed channel, read timeout " + ctx.channel().localAddress()
					+ " from " + ctx.channel().remoteAddress()); // + " session " + this.context.getSession().getId());
		}
		else {
			Logger.warn("Web server connection exception was " + cause);
			
			if (Logger.isDebug())
				Logger.debug("Web server connection exception was " + ctx.channel().localAddress()
						+ " from " + ctx.channel().remoteAddress()); // + " session " + this.context.getSession().getId());
		}
		
		ctx.close();
		
		OperationContext wctx = this.context;
		
		this.context = null;
		
		if (wctx != null)
			((WebController) wctx.getController()).closed();
	}
	
	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		if (Logger.isDebug())
			Logger.debug("Connection inactive was " + ctx.channel().localAddress()
					+ " from " + ctx.channel().remoteAddress());
		
		OperationContext wctx = this.context;
		
		this.context = null;
		
		if (wctx != null) {
			Logger.info("Web Server connection inactive: " + wctx.getSessionId());
			((WebController) this.context.getController()).closed();
		}
	}
	
	/* TODO adapter review
	static public class HttpAdapter implements ISessionAdapter {
		protected volatile ListStruct msgs = new ListStruct();
	    protected HttpContext context = null;
		
		public HttpAdapter(HttpContext ctx) {
			this.context = ctx;
		}
		
		@Override
		public void stop() {
	    	if (Logger.isDebug())
	    		Logger.debug("Web server session adapter got a STOP request.");
	    	
			this.context.close();
		}
		
		@Override
		public String getClientKey() {
			return this.context.getClientCert();
		}
		
		@Override
		public ListStruct popMessages() {
			ListStruct ret = this.msgs;
			this.msgs = new ListStruct();
			return ret;
		}
		
		@Override
		public void deliver(Message msg) {
			// keep no more than 100 messages - this is not a "reliable" approach, just basic comm help
			while (this.msgs.getSize() > 99)
				this.msgs.removeItem(0);
			
			this.msgs.addItem(msg);
		}

		@Override
		public void UserChanged(UserContext user) {
			// we use the checkTokenUpdate approach instead
		}
	}
	*/

}
