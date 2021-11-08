package dcraft.tenant;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dcraft.filestore.CollectionSourceStream;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileCollection;
import dcraft.filestore.mem.MemoryStoreFile;
import dcraft.filevault.FileStoreVault;
import dcraft.filevault.Vault;
import dcraft.filestore.local.LocalStore;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.resource.ResourceTier;
import dcraft.locale.LocaleDefinition;
import dcraft.log.Logger;
import dcraft.stream.StreamUtil;
import dcraft.struct.*;
import dcraft.util.IOUtil;
import dcraft.util.StringUtil;
import dcraft.web.*;
import dcraft.web.adapter.*;
import dcraft.web.ui.UIUtil;
import dcraft.web.ui.inst.W3;
import dcraft.xml.XElement;
import dcraft.xml.XmlReader;
import io.netty.handler.ssl.SslHandler;

/*
 * The "root" site never comes from the /alias/sites/root/* folder.  It always uses the configuration
 * for the domain.  It's files are in /alias/www, /alias/feeds, etc
 */

public class Site extends Base {
	static final public CommonPath PATH_INDEX = new CommonPath("/index");
	static final public CommonPath PATH_HOME = new CommonPath("/home");
	static final public CommonPath PATH_NOT_FOUND = new CommonPath("/not-found");

	static final public String[] EXTENSIONS_STD = new String[] { ".html", ".md", ".dcs.xml" };

	static public Site of(Tenant tenant, String alias) {
		Site site = new Site();
		site.with("Tenant", tenant);
		site.with("Alias", alias);
		site.isRoot = alias.equals("root");
		
		return site;
	}
	
	protected boolean isRoot = false;
	protected SiteIntegration integration = SiteIntegration.Files;
	
	protected ResourceTier config = null;
	//protected GCompClassLoader scriptloader = null;

	protected List<String> domains = new ArrayList<>();
	protected Map<String, LocaleDefinition> sitelocales = new HashMap<>();

	protected Map<String, Vault> vaults = new HashMap<>();

	protected HtmlMode htmlmode = HtmlMode.Dynamic;
	protected CommonPath homepath = Site.PATH_HOME;
	protected CommonPath notfoundpath = null;
	protected String[] specialExtensions = Site.EXTENSIONS_STD;
	protected boolean srcptstlcache = false;
	protected List<XElement> webglobals = null;
	protected String webversion = "7001010000";		// YYMMDDhhmm
	
	protected Map<String, IWebWorkBuilder> dynadapaters = new HashMap<>();
	protected Map<String, IWebWorkBuilder> dynextadapaters = new HashMap<>();

	public HtmlMode getHtmlMode() {
		return this.htmlmode;
	}

	public void setHtmlMode(HtmlMode v) {
		this.htmlmode = v;
	}

	public CommonPath getHomePath() {
		return this.homepath;
	}

	public void setHomePath(CommonPath v) {
		this.homepath = v;
	}
	
	public void setWebGlobals(List<XElement> v) { this.webglobals = v; }

	public boolean isScriptStyleCached() {
		return this.srcptstlcache;
	}

	public void setScriptCache(boolean v) {
		this.srcptstlcache = v;
	}
	
	public void setWebVersion(String v) {
		this.webversion = v;
	}
	
	public String getWebVersion() {
		return this.webversion;
	}
	
	public void addDynamicAdapater(String name, IWebWorkBuilder builder) {
		this.dynadapaters.put(name, builder);
	}

	public void addDynamicExtAdapater(String ext, IWebWorkBuilder builder) {
		this.dynextadapaters.put(ext, builder);
	}

	public void setSpecialExtensions(List<String> v) {
		this.specialExtensions = v.toArray(new String[v.size()]);
	}

	public void setNotFoundPath(CommonPath v) {
		this.notfoundpath = v;
	}

	public CommonPath getNotFoundPath() {
		return this.notfoundpath;
	}

	public Tenant getTenant() {
		return (Tenant) this.getFieldAsRecord("Tenant");
	}
	
	public boolean isRoot() {
		return this.isRoot;
	}
	
	public List<String> getDomains() {
		return this.domains;
	}
	
	public Site withDomain(String v) {
		this.domains.add(v);
		return this;
	}
	
	public Site withLocaleDomain(String domain, LocaleDefinition def) {
		this.domains.add(domain);
		this.sitelocales.put(domain, def);
		return this;
	}
	
	public LocaleDefinition getLocaleDomain(String domain) {
		return this.sitelocales.get(domain);
	}
	
	public ResourceTier getResources() {
		if (this.config != null)
			return this.config;
		
		return this.getTenant().getResources();
	}
	
	public ResourceTier getTierResources() {
		return this.config;
	}
	
	public ResourceTier getResourcesOrCreate(ResourceTier tier) {
		if (this.config == null)
			this.config = ResourceTier.tier(tier);
		
		return this.config;
	}

	public SiteIntegration getIntegration() {
		if (this.isRoot())
			return SiteIntegration.None;
		
		return this.integration;
	}
	
	public Site withIntegration(SiteIntegration v) {
		this.integration = v;
		return this;
	}
	
	public boolean isSharedSection(String section) {
		if (this.isRoot())
			return false;
		
		if (this.integration == SiteIntegration.None)
			return false;
		
		if (this.integration == SiteIntegration.Full)
			return true;

		return ("files".equals(section) || "galleries".equals(section));
	}

	@Override
	public Path getPath() {
		if (this.isRoot())
			return this.getTenant().getPath();
		
		LocalStore fs = TenantHub.getFileStore();
		
		if (fs == null)
			return null;
		
		return fs.resolvePath(this.getTenant().getAlias() + "/sites/" + this.getAlias());
	}
	
	public XElement getFeeds() {
		return null;
	}
	
	public Path findSectionFile(String section, String path, String view) {
		if (Logger.isDebug())
			Logger.debug("find section file: " + path + " in " + section);
		
		// for a sub-site, check first in the site folder
		
		if (Logger.isDebug())
			Logger.debug("find section file, check site: " + path + " in " + section);
		
		Path cfile = null;
		
		if (StringUtil.isNotEmpty(view)) {
			cfile = this.resolvePath("/" + section + "-" + view + path);

			if (Files.exists(cfile))
				return cfile;
		}
			
		cfile = this.resolvePath("/" + section + path);
		
		if (Files.exists(cfile))
			return cfile;
		
		// if not root and if shared then try the root level files
		if (! this.isRoot() && this.isSharedSection(section)) {
			// now check the root site folders

			if (Logger.isDebug())
				Logger.debug("find section file, check root: " + path + " in " + section);
			
			if (StringUtil.isNotEmpty(view)) {
				cfile = this.getTenant().resolvePath("/" + section + "-" + view + path);
				
				if (Files.exists(cfile))
					return cfile;
			}
			
			cfile = this.getTenant().resolvePath("/" + section + path);
			
			if (Files.exists(cfile))
				return cfile;
			
			if (Logger.isDebug())
				Logger.debug("find section check packages: " + path + " in " + section);
		}
		
		// check in the modules
		return ResourceHub.getResources().getPackages().lookupPath("/" + section + path);
	}
	
	// string path is relative to tenants/[alias]/[path]
	public XElement getXmlResource(String section, String path, String view) {
		Path fpath = this.findSectionFile(section, path, view);
		
		if (fpath == null)
			return null;
		
		return XmlReader.loadFile(fpath, true, true);
	}
	
	// string path is relative to tenants/[alias]/[section]/[path]
	public CompositeStruct getJsonResource(String section, String path, String view) {
		Path fpath = this.findSectionFile(section, path, view);
		
		if (fpath == null)
			return null;
		
		return CompositeParser.parseJson(fpath);
	}
	
	// string path is relative to tenants/[alias]/[section]/[path]
	public String getTextResource(String section, String path, String view) {
		Path fpath = this.findSectionFile(section, path, view);
		
		if (fpath == null)
			return null;
		
		return IOUtil.readEntireFile(fpath).toString();
	}
	
	public Collection<Vault> getSiteVaults() throws OperatingContextException {
		List<XElement> vaults = this.getResources().getConfig().getTagListDeep("Vaults/Site");

		for (XElement bucket : vaults) {
			String id = bucket.getAttribute("Id");

			if (StringUtil.isEmpty(id) || this.vaults.containsKey(id))
				continue;

			Vault b = Vault.of(this, bucket);

			if (b != null)
				this.vaults.put(id, b);
		}

		List<Vault> copy = new ArrayList<>();

		copy.addAll(this.vaults.values());
		
		if ("root".equals(this.getAlias()))
			copy.addAll(this.getTenant().getVaults());
		
		return copy;
	}
	
	public Collection<Vault> getVaults() throws OperatingContextException {
		List<XElement> vaults = this.getResources().getConfig().getTagListDeep("Vaults/Site");

		for (XElement bucket : vaults) {
			String id = bucket.getAttribute("Id");

			if (StringUtil.isEmpty(id) || this.vaults.containsKey(id))
				continue;

			Vault b = Vault.of(this, bucket);

			if (b != null)
				this.vaults.put(id, b);
		}

		List<Vault> copy = new ArrayList<>();

		copy.addAll(this.vaults.values());

		copy.addAll(this.getTenant().getVaults());

		return copy;
	}

	@Override
	public Vault getVault(String id) throws OperatingContextException {
		// like tenant database - this is shared data
		Vault b = this.vaults.get(id);

		if (b == null) {
			XElement bucket = this.getResources().getConfig().findId("Vaults/Site", id);

			if (bucket != null) {
				b = Vault.of(this, bucket);

				if (b != null)
					this.vaults.put(id, b);
			}

			XElement map = this.getResources().getConfig().findId("Vaults/RootMap", id);

			if (map != null) {
				return this.getTenant().getRootSite().getVault(map.getAttribute("Alias"));
			}
		}

		if (b != null)
			return b;

		return this.getTenant().getVault(id);
	}
	
	public FileStoreVault getGalleriesVault() throws OperatingContextException {
		Vault vault = this.getVault("Galleries");
		
		if ((vault != null) && (vault instanceof FileStoreVault))
			return (FileStoreVault) vault;
		
		Logger.error("Missing or badly configured Galleries vault");
		return null;
	}
	
	public FileStoreVault getFilesVault() throws OperatingContextException {
		Vault vault = this.getVault("Files");
		
		if ((vault != null) && (vault instanceof FileStoreVault))
			return (FileStoreVault) vault;
		
		Logger.error("Missing or badly configured Files vault");
		return null;
	}
	
	public FileStoreVault getFeedsVault() throws OperatingContextException {
		Vault vault = this.getVault("Feeds");
		
		if ((vault != null) && (vault instanceof FileStoreVault))
			return (FileStoreVault) vault;
		
		Logger.error("Missing or badly configured Feeds vault");
		return null;
	}
	
	public FileStoreVault getSiteFilesVault() throws OperatingContextException {
		Vault vault = this.getVault("SiteFiles");
		
		if ((vault != null) && (vault instanceof FileStoreVault))
			return (FileStoreVault) vault;
		
		Logger.error("Missing or badly configured SiteFiles vault");
		return null;
	}
	
	public List<XElement> webGlobalStyles(boolean cachmode) throws OperatingContextException {
		List<XElement> ret = new ArrayList<>();
		
		// something is wrong
		if (this.webglobals == null)
			return ret;

		for (XElement gel : this.webglobals) {
			if (gel.hasNotEmptyAttribute("Style")) {
				String surl = gel.getAttribute("Style");

				if (! UIUtil.urlLooksLocal(surl))
					ret.add(W3.tag("link")
							.withAttribute("type", "text/css")
							.withAttribute("rel", "stylesheet")
							.attr("href", surl)
					);
			}
		}
		
		if (cachmode) {
			ret.add(W3.tag("link")
					.withAttribute("type", "text/css")
					.withAttribute("rel", "stylesheet")
					.attr("href", "/css/dc.cache.css?_dcver=" + OperationContext.getOrThrow().getSite().getWebVersion())
			);

			// TODO replace with dc-cache once tenant auto reload is in place
		}
		else {
			for (XElement gel : this.webglobals) {
				if (gel.hasNotEmptyAttribute("Style")) {
					String surl = gel.getAttribute("Style");
					
					if (UIUtil.urlLooksLocal(surl))
						ret.add(W3.tag("link")
								.withAttribute("type", "text/css")
								.withAttribute("rel", "stylesheet")
								.attr("href", surl)
						);
				}
			}
			
			ret.add(W3.tag("link")
					.withAttribute("type", "text/css")
					.withAttribute("rel", "stylesheet")
					.attr("href", "/css/main.css")
			);        // site specifics and overrides
		}
		
		return ret;
	}
	
	public CollectionSourceStream webCacheStyles() throws OperatingContextException {
		FileCollection collection = new FileCollection();
		
		if (this.webglobals != null) {
			// local global scripts
			for (XElement gel : this.webglobals) {
				if (gel.hasNotEmptyAttribute("Style")) {
					String surl = gel.getAttribute("Style");
					
					if (UIUtil.urlLooksLocal(surl)) {
						WebFindResult glb = this.webFindFilePath(CommonPath.from(surl), null);
						
						if (glb != null)
							collection.withFiles(
									MemoryStoreFile.of(CommonPath.from(surl + ".txt"))
											.with("\n\n/* \n *\n * START: " + surl + "\n *\n */\n\n"),
									StreamUtil.localFile(glb.file)
							);
					}
				}
			}
			
			// site specifics and overrides
			WebFindResult main = this.webFindFilePath(CommonPath.from("/css/main.css"), null);
			
			if (main != null)
				collection.withFiles(
						MemoryStoreFile.of(CommonPath.from("/css/main.css.txt"))
								.with("\n\n/* \n *\n * START: /css/main.css\n *\n */\n\n"),
						StreamUtil.localFile(main.file)
				);
		}
		
		return CollectionSourceStream.of(collection);
	}

	public List<XElement> webGlobalScripts(boolean cachmode) throws OperatingContextException {
		List<XElement> ret = new ArrayList<>();

		if (this.webglobals == null)
			return ret;
		
		// external non-async
		for (XElement gel : this.webglobals) {
			if (gel.hasNotEmptyAttribute("Script")) {
				String surl = gel.getAttribute("Script");

				if (! UIUtil.urlLooksLocal(surl) && ! gel.getAttributeAsBooleanOrFalse("Async"))
					ret.add(
							W3.tag("script")
									.attr("defer", "defer")
									.attr("src", surl)
					);
			}
		}

		if (cachmode) {
			ret.add(
					W3.tag("script")
						.attr("defer", "defer")
						.attr("src", "/js/dc.cache.js?_dcver=" + OperationContext.getOrThrow().getSite().getWebVersion())
			);

			// TODO replace with dc-cache once tenant auto reload is in place
		}
		else {
			for (XElement gel : this.webglobals) {
				if (gel.hasNotEmptyAttribute("Script")) {
					String surl = gel.getAttribute("Script");
					
					if (UIUtil.urlLooksLocal(surl)) {
						ret.add(
								W3.tag("script")
										.attr("defer", "defer")
										.attr("src", surl)
						);
					}
				}
			}
			
			// site specifics and overrides
			ret.add(
					W3.tag("script")
							.attr("defer", "defer")
							.attr("src", "/js/main.js")
			);
			
			// start the UI scripts
			ret.add(
					W3.tag("script")
							.attr("defer", "defer")
							.attr("src", "/js/dc.go.js")
			);
		}

		// finally external, async
		for (XElement gel : this.webglobals) {
			if (gel.hasNotEmptyAttribute("Script")) {
				String surl = gel.getAttribute("Script");
				
				if (! UIUtil.urlLooksLocal(surl) && gel.getAttributeAsBooleanOrFalse("Async"))
					ret.add(
							W3.tag("script")
									.attr("defer", "defer")
									.attr("async", "async")
									.attr("src", surl)
					);
			}
		}

		return ret;
	}
	
	public CollectionSourceStream webCacheScripts() throws OperatingContextException {
		FileCollection collection = new FileCollection();
		
		if (this.webglobals != null) {
			// local global scripts
			for (XElement gel : this.webglobals) {
				if (gel.hasNotEmptyAttribute("Script")) {
					String surl = gel.getAttribute("Script");
					
					if (UIUtil.urlLooksLocal(surl)) {
						WebFindResult glb = this.webFindFilePath(CommonPath.from(surl), null);
						
						if (glb != null)
							collection.withFiles(
									MemoryStoreFile.of(CommonPath.from(surl + ".txt"))
										.with("\n\n/* \n *\n * START: " + surl + "\n *\n */\n\n"),
									StreamUtil.localFile(glb.file)
							);
					}
				}
			}
			
			// site specifics and overrides
			WebFindResult main = this.webFindFilePath(CommonPath.from("/js/main.js"), null);
			
			if (main != null)
				collection.withFiles(
						MemoryStoreFile.of(CommonPath.from("/js/main.js.txt"))
								.with("\n\n/* \n *\n * START: /js/main.js\n *\n */\n\n"),
						StreamUtil.localFile(main.file)
				);
			
			// start the UI scripts
			WebFindResult go = this.webFindFilePath(CommonPath.from("/js/dc.go.js"), null);
			
			if (go != null)
				collection.withFiles(
						MemoryStoreFile.of(CommonPath.from("/js/dc.go.js.txt"))
								.with("\n\n/* \n *\n * START: /js/dc.go.js\n *\n */\n\n"),
						StreamUtil.localFile(go.file)
				);
		}
		
		return CollectionSourceStream.of(collection);
	}

	// TODO make sure all routing is done on lower case paths
	public String webRoute(WebController ctrl, SslHandler ssl) {
		RecordStruct request = ctrl.getFieldAsRecord("Request");
		
		String host = request.getFieldAsString("Host");
		String port = "";

		if (host.contains(":")) {
			int pos = host.indexOf(':');
			port = host.substring(pos);
			host = host.substring(0, pos);
		}

		//String defPort = ((WebModule) Hub.instance.getModule("Web")).getDefaultTlsPort();
		String defPort = ctrl.getServiceSettings().getAttribute("DefaultSecurePort", "443");

		String orgpath = request.getFieldAsString("Path");
		
		// a little hacky - never redirect on an ACME challenge. could make this more generalized
		if (orgpath.startsWith("/.well-known/acme-challenge"))
			return null;

		for (XElement config : ResourceHub.getResources().getConfig().getTagListDeep("Web")) {
			for (XElement route : config.selectAll("Route")) {
				if (host.equals(route.getAttribute("Name"))) {
					if (route.hasAttribute("RedirectPath"))
						return route.getAttribute("RedirectPath");

					if (route.hasAttribute("RedirectUrl"))
						return route.getAttribute("RedirectUrl");

					if (!route.hasAttribute("ForceTls") && !route.hasAttribute("RedirectName"))
						continue;

					boolean tlsForce = Struct.objectToBoolean(route.getAttribute("ForceTls", "False"));
					String rname = route.getAttribute("RedirectName");

					boolean changeTls = ((ssl == null) && tlsForce);

					if (StringUtil.isNotEmpty(rname) || changeTls) {
						boolean tlsUse = ((ssl != null) || tlsForce);

						String path = tlsUse ? "https://" : "http://";

						path += StringUtil.isNotEmpty(rname) ? rname : host;

						// if forcing a switch, use another port
						path += tlsUse ? ":" + route.getAttribute("TlsPort", defPort) : port;

						if (request.isNotFieldEmpty("Query"))
							return path + orgpath + "?" + request.getFieldAsString("Query");
						else
							return path + orgpath;
					}

					continue;
				}

				if (orgpath.equals(route.getAttribute("Path"))) {
					if (route.hasAttribute("RedirectPath"))
						return route.getAttribute("RedirectPath");

					if (route.hasAttribute("RedirectUrl"))
						return route.getAttribute("RedirectUrl");
				}
			}

			if ((ssl == null) && Struct.objectToBoolean(config.getAttribute("ForceTls", "False")))
				if (request.isNotFieldEmpty("Query"))
					return "https://" + host + ":" + config.getAttribute("TlsPort", defPort) + orgpath + "?" + request.getFieldAsString("Query");
				else
					return "https://" + host + ":" + config.getAttribute("TlsPort", defPort) + orgpath;
		}

		return null;
	}

	public IOutputWork webFindFile(CommonPath path, String view) throws OperatingContextException {
		// =====================================================
		//  if request has an extension do specific file lookup
		// =====================================================

		if (Logger.isDebug())
			Logger.debug("find file before ext check: " + path + " - " + view);

		// if we have an extension then we don't have to do the search below
		// never go up a level past a file (or folder) with an extension
		if (path.hasFileExtension()) {
			if (this.dynadapaters.containsKey(path.toString()))
				return this.dynadapaters.get(path.toString()).buildOutputAdapter(this, null, path, view);
			
			WebFindResult wpath = this.webFindFilePath(path, view);

			if (wpath != null)
				return this.webPathToAdapter(view, wpath);

			// let caller decide if error - Logger.errorTr(150007);
			return null;
		}

		// =====================================================
		//  if request does not have an extension look for files
		//  that might match this path or one of its parents
		//  using the special extensions
		// =====================================================

		if (Logger.isDebug())
			Logger.debug("find dyn file: " + path + " - " + view);
		
		WebFindResult wpath = this.webFindFilePath(path, view);

		if (wpath == null) {
			// let caller decide if error - Logger.errorTr(150007);
			return null;
		}

		if (Logger.isDebug())
			Logger.debug("find file path: " + wpath + " - " + path + " - " + view);

		return this.webPathToAdapter(view, wpath);
	}

	public IOutputWork webPathToAdapter(String view, WebFindResult wpath) throws OperatingContextException {
		IOutputWork ioa = null;

		String filename = wpath.file.getFileName().toString();
		CommonPath path = wpath.path;
		int ldot = filename.lastIndexOf('.');
		String ext = (ldot >= 0) ? filename.substring(ldot) : null;

		HtmlMode hmode = this.getHtmlMode();

		// /galleries and /files always processed by static output because these areas can be altered by Editors and Admins
		// only developers and sysadmins should make changes that can run server scripts
		if ((path.getNameCount() > 1) && ("galleries".equals(path.getName(0)) || "files".equals(path.getName(0)))) {
			ioa = new StaticOutputAdapter();
		}
		else if (StringUtil.isNotEmpty(ext) && this.dynextadapaters.containsKey(ext)) {
			ioa = this.dynextadapaters.get(ext).buildOutputAdapter(this, null, path, view);
		}
		else if (filename.endsWith(".html")) {
			if (hmode == HtmlMode.Ssi)
				ioa = new SsiOutputAdapter();
			else if ((hmode == HtmlMode.Dynamic) || (hmode == HtmlMode.Strict))
				ioa = new DynamicOutputAdapter();
			else if (hmode == HtmlMode.Static)
				ioa = new StaticOutputAdapter();
		}
		else if (filename.endsWith(".md")) {
			ioa = new MarkdownOutputAdapter();
		}
		else if (filename.endsWith(".dcs.xml")) {
			ioa = new ScriptOutputAdapter();
		}
		else {
			ioa = new StaticOutputAdapter();
		}

		ioa.init(this, wpath.file, path, view);

		return ioa;
	}

	public IReviewWork webFindReviewFile(CommonPath path, String view) throws OperatingContextException {
		// =====================================================
		//  if request has an extension do specific file lookup
		// =====================================================

		if (Logger.isDebug())
			Logger.debug("find file before ext check: " + path + " - " + view);

		// if we have an extension then we don't have to do the search below
		// never go up a level past a file (or folder) with an extension
		if (path.hasFileExtension()) {
			if (this.dynadapaters.containsKey(path.toString()))
				return this.dynadapaters.get(path.toString()).buildReviewAdapter(this, null, path, view);

			WebFindResult wpath = this.webFindFilePath(path, view);

			if (wpath != null)
				return this.webPathToReviewAdapter(view, wpath);

			// TODO not found file!!
			// let caller decide if error - Logger.errorTr(150007);
			return null;
		}

		// =====================================================
		//  if request does not have an extension look for files
		//  that might match this path or one of its parents
		//  using the special extensions
		// =====================================================

		if (Logger.isDebug())
			Logger.debug("find dyn file: " + path + " - " + view);

		WebFindResult wpath = this.webFindFilePath(path, view);

		if (wpath == null) {
			// let caller decide if error - Logger.errorTr(150007);
			return null;
		}

		if (Logger.isDebug())
			Logger.debug("find file path: " + wpath + " - " + path + " - " + view);

		return this.webPathToReviewAdapter(view, wpath);
	}

	public IReviewWork webPathToReviewAdapter(String view, WebFindResult wpath) throws OperatingContextException {
		IReviewWork ioa = null;

		String filename = wpath.file.getFileName().toString();
		CommonPath path = wpath.path;

		HtmlMode hmode = this.getHtmlMode();

		// currently only supports Dynamic
		if (filename.endsWith(".html")) {
			if ((hmode == HtmlMode.Dynamic) || (hmode == HtmlMode.Strict))
				ioa = new DynamicReviewAdapter();
		}
		else if (filename.endsWith(".md")) {
			ioa = new MarkdownReviewAdapter();
		}
		else {
			return null;
		}

		ioa.init(this, wpath.file, path, view);

		return ioa;
	}

	public WebFindResult webFindFilePath(CommonPath path, String view) {
		// figure out which section we are looking in
		String sect = "www";
		
		if ("files".equals(path.getName(0)) || "galleries".equals(path.getName(0))) {
			sect = path.getName(0);
			path = path.subpath(1);
		}
		
		if (Logger.isDebug())
			Logger.debug("find file path: " + path + " in " + sect);
		
		// =====================================================
		//  if request has an extension do specific file lookup
		// =====================================================
		
		// if we have an extension then we don't have to do the search below
		// never go up a level past a file (or folder) with an extension
		if (path.hasFileExtension()) {
			Path spath = this.findSectionFile(sect, path.toString(), view);

			if (spath == null)
				return null;

			return WebFindResult.of(spath, path);
		}

		// =====================================================
		//  if request does not have an extension look for files
		//  that might match this path or one of its parents
		//  using the special extensions
		// =====================================================
		
		if (Logger.isDebug())
			Logger.debug("find file path dyn: " + path + " in " + sect);
		
		// we get here if we have no extension - thus we need to look for path match with specials
		int pdepth = path.getNameCount();
		
		// check file system
		while (pdepth > 0) {
			CommonPath ppath = path.subpath(0, pdepth);
			
			// we want to check all extensions at folder level then go up the path
			for (String ext : this.specialExtensions) {
				Path cfile = this.findSectionFile(sect, ppath.toString() + ext, view);
				
				if (cfile != null)
					return WebFindResult.of(cfile, ppath);
			}
			
			pdepth--;
		}

		// for no extension
		Path cfile = this.findSectionFile(sect, path.toString(), view);

		if (cfile != null)
			return WebFindResult.of(cfile, path);

		// let caller decide if error - Logger.errorTr(150007);
		return null;
	}

	@Override
	public BaseStruct queryVariable(String name) {
		BaseStruct res = super.queryVariable(name);

		if (res == null)
			res = this.getTenant().queryVariable(name);

		return res;
	}
}
