package dcraft.tenant;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dcraft.filestore.CommonPath;
import dcraft.filevault.FeedVault;
import dcraft.filevault.GalleryVault;
import dcraft.filevault.Vault;
import dcraft.filestore.local.LocalStore;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.resource.ResourceTier;
import dcraft.locale.LocaleDefinition;
import dcraft.log.Logger;
import dcraft.struct.CompositeParser;
import dcraft.struct.CompositeStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.IOUtil;
import dcraft.util.StringUtil;
import dcraft.util.groovy.GCompClassLoader;
import dcraft.web.HtmlMode;
import dcraft.web.IOutputWork;
import dcraft.web.WebController;
import dcraft.web.adapter.DynamicOutputAdapter;
import dcraft.web.adapter.MarkdownOutputAdapter;
import dcraft.web.adapter.SsiOutputAdapter;
import dcraft.web.adapter.StaticOutputAdapter;
import dcraft.web.ui.UIUtil;
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

	static final public String[] EXTENSIONS_STD = new String[] { ".html", ".md", ".gas" };

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
	protected String[] specialExtensions = Site.EXTENSIONS_STD;
	protected boolean srcptstlcache = false;
	protected List<XElement> webglobals = null;

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

	public CommonPath getNotFound() {
		if (this.homepath != null)
			return this.homepath;

		return new CommonPath("/not-found.html");
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
	
	public LocaleDefinition getLocaleDefinition(String domain) {
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
	
	public Collection<Vault> getVaults() {
		// prime the collection TODO remove priming when we load from config/package
		this.getVault("Galleries");
		this.getVault("Files");
		this.getVault("Feeds");
		this.getVault("Web");
		this.getVault("Templates");
		this.getVault("Emails");
		this.getVault("Config");
		this.getVault("StoreOrders");
		this.getVault("ManagedForms");
		
		return this.vaults.values();
	}

	@Override
	public Vault getVault(String name) {
		if ("Galleries".equals(name)) {
			if (! this.vaults.containsKey(name)) {
				XElement vconfig = this.getResources().getConfig().findId("Vault", name);
				
				if (vconfig == null)
					vconfig = XElement.tag("Vault").withAttribute("Id", "Galleries")
							.withAttribute("ReadAuthTags", "Editor,Admin")
							.withAttribute("WriteAuthTags", "Editor,Admin")
							.withAttribute("RootFolder", "/galleries");
				
				Vault vault = vconfig.hasNotEmptyAttribute("VaultClass")
						? (Vault) this.getResources().getClassLoader().getInstance(vconfig.getAttribute("VaultClass"))
						: new GalleryVault();

				vault.init(this, vconfig, null);

				this.vaults.put(name, vault);
			}
			
			return this.vaults.get(name);
		}
		
		if ("Files".equals(name)) {
			if (! this.vaults.containsKey(name)) {
				XElement vconfig = this.getResources().getConfig().findId("Vault", name);
				
				if (vconfig == null)
					vconfig = XElement.tag("Vault").withAttribute("Id", "Files")
							.withAttribute("ReadAuthTags", "Editor,Admin")
							.withAttribute("WriteAuthTags", "Editor,Admin")
							.withAttribute("RootFolder", "/files");

				Vault vault = vconfig.hasNotEmptyAttribute("VaultClass")
						? (Vault) this.getResources().getClassLoader().getInstance(vconfig.getAttribute("VaultClass"))
						: new Vault();

				vault.init(this, vconfig, null);

				this.vaults.put(name, vault);
			}

			return this.vaults.get(name);
		}

		if ("Feeds".equals(name)) {
			if (! this.vaults.containsKey(name)) {
				XElement vconfig = this.getResources().getConfig().findId("Vault", name);

				if (vconfig == null)
					vconfig = XElement.tag("Vault").withAttribute("Id", "Feeds")
							.withAttribute("ReadAuthTags", "Editor,Admin")
							.withAttribute("WriteAuthTags", "Editor,Admin")
							.withAttribute("RootFolder", "/feeds");

				Vault vault = vconfig.hasNotEmptyAttribute("VaultClass")
						? (Vault) this.getResources().getClassLoader().getInstance(vconfig.getAttribute("VaultClass"))
						: new FeedVault();

				vault.init(this, vconfig, null);

				this.vaults.put(name, vault);
			}

			return this.vaults.get(name);
		}

		if ("Web".equals(name)) {
			if (! this.vaults.containsKey(name)) {
				XElement vconfig = this.getResources().getConfig().findId("Vault", name);

				if (vconfig == null)
					vconfig = XElement.tag("Vault").withAttribute("Id", "Web")
							.withAttribute("ReadAuthTags", "Developer")
							.withAttribute("WriteAuthTags", "Developer")
							.withAttribute("RootFolder", "/www");

				Vault vault = vconfig.hasNotEmptyAttribute("VaultClass")
						? (Vault) this.getResources().getClassLoader().getInstance(vconfig.getAttribute("VaultClass"))
						: new Vault();

				vault.init(this, vconfig, null);

				this.vaults.put(name, vault);
			}

			return this.vaults.get(name);
		}

		if ("Templates".equals(name)) {
			if (! this.vaults.containsKey(name)) {
				XElement vconfig = this.getResources().getConfig().findId("Vault", name);

				if (vconfig == null)
					vconfig = XElement.tag("Vault").withAttribute("Id", "Templates")
							.withAttribute("ReadAuthTags", "Developer")
							.withAttribute("WriteAuthTags", "Developer")
							.withAttribute("RootFolder", "/templates");

				Vault vault = vconfig.hasNotEmptyAttribute("VaultClass")
						? (Vault) this.getResources().getClassLoader().getInstance(vconfig.getAttribute("VaultClass"))
						: new Vault();

				vault.init(this, vconfig, null);

				this.vaults.put(name, vault);
			}

			return this.vaults.get(name);
		}

		if ("Emails".equals(name)) {
			if (! this.vaults.containsKey(name)) {
				XElement vconfig = this.getResources().getConfig().findId("Vault", name);

				if (vconfig == null)
					vconfig = XElement.tag("Vault").withAttribute("Id", "Emails")
							.withAttribute("ReadAuthTags", "Developer")
							.withAttribute("WriteAuthTags", "Developer")
							.withAttribute("RootFolder", "/emails");

				Vault vault = vconfig.hasNotEmptyAttribute("VaultClass")
						? (Vault) this.getResources().getClassLoader().getInstance(vconfig.getAttribute("VaultClass"))
						: new Vault();

				vault.init(this, vconfig, null);

				this.vaults.put(name, vault);
			}

			return this.vaults.get(name);
		}

		if ("Config".equals(name)) {
			if (! this.vaults.containsKey(name)) {
				XElement vconfig = this.getResources().getConfig().findId("Vault", name);

				if (vconfig == null)
					vconfig = XElement.tag("Vault").withAttribute("Id", "Config")
							.withAttribute("ReadAuthTags", "Developer")
							.withAttribute("WriteAuthTags", "Developer")
							.withAttribute("RootFolder", "/config");

				Vault vault = vconfig.hasNotEmptyAttribute("VaultClass")
						? (Vault) this.getResources().getClassLoader().getInstance(vconfig.getAttribute("VaultClass"))
						: new Vault();

				vault.init(this, vconfig, null);

				this.vaults.put(name, vault);
			}

			return this.vaults.get(name);
		}

		// TODO move into config, really all these should be in config
		if ("StoreOrders".equals(name)) {
			if (! this.vaults.containsKey(name)) {
				XElement vconfig = this.getResources().getConfig().findId("Vault", name);

				if (vconfig == null)
					vconfig = XElement.tag("Vault").withAttribute("Id", "StoreOrders")
							.withAttribute("ReadAuthTags", "Developer")
							.withAttribute("WriteAuthTags", "Developer");

				Vault vault = vconfig.hasNotEmptyAttribute("VaultClass")
						? (Vault) this.getResources().getClassLoader().getInstance(vconfig.getAttribute("VaultClass"))
						: new Vault();

				vault.init(this, vconfig, null);

				this.vaults.put(name, vault);
			}

			return this.vaults.get(name);
		}
		
		if ("ManagedForms".equals(name)) {
			if (! this.vaults.containsKey(name)) {
				XElement vconfig = this.getResources().getConfig().findId("Vault", name);
				
				if (vconfig == null)
					vconfig = XElement.tag("Vault").withAttribute("Id", "ManagedForms")
							.withAttribute("ReadAuthTags", "Admin")
							.withAttribute("WriteAuthTags", "Admin");
				
				Vault vault = vconfig.hasNotEmptyAttribute("VaultClass")
						? (Vault) this.getResources().getClassLoader().getInstance(vconfig.getAttribute("VaultClass"))
						: new Vault();
				
				vault.init(this, vconfig, null);
				
				this.vaults.put(name, vault);
			}
			
			return this.vaults.get(name);
		}
		
		return this.getTenant().getVault(name);
	}
	
	/*
	@Override
	public GCompClassLoader getScriptLoader() {
		if (this.scriptloader == null) {
			this.scriptloader = new GCompClassLoader();
			this.scriptloader.init(this.resolvePath("cache"), this.resolvePath("glib"));
		}
		
		return this.scriptloader;
	}
	*/

	public List<String> webGlobalStyles(boolean includeWebRemote, boolean cachmode) {
		List<String> ret = new ArrayList<>();

		if (this.webglobals != null) {
			if (includeWebRemote) {
				for (XElement gel : this.webglobals) {
					if (gel.hasNotEmptyAttribute("Style")) {
						String surl = gel.getAttribute("Style");

						if (! UIUtil.urlLooksLocal(surl))
							ret.add(surl);
					}
				}
			}

			if (cachmode) {
				ret.add("/css/dc-cache.min.css");
				return ret;
			}

			for (XElement gel : this.webglobals) {
				if (gel.hasNotEmptyAttribute("Style")) {
					String surl = gel.getAttribute("Style");

					if (UIUtil.urlLooksLocal(surl))
						ret.add(surl);
				}
			}
		}
		
		ret.add("/css/main.css");        // site specifics and overrides

		return ret;
	}

	public List<String> webGlobalScripts(boolean includeWebRemote, boolean cachmode) {
		List<String> ret = new ArrayList<>();

		if (this.webglobals != null) {
			if (includeWebRemote) {
				for (XElement gel : this.webglobals) {
					if (gel.hasNotEmptyAttribute("Script")) {
						String surl = gel.getAttribute("Script");

						if (! UIUtil.urlLooksLocal(surl))
							ret.add(surl);
					}
				}
			}

			if (cachmode) {
				ret.add("/js/dc-cache.min.js");
				return ret;
			}
			
			for (XElement gel : this.webglobals) {
				if (gel.hasNotEmptyAttribute("Script")) {
					String surl = gel.getAttribute("Script");

					if (UIUtil.urlLooksLocal(surl))
						ret.add(surl);
				}
			}

			ret.add("/js/main.js");        // site specifics and overrides

			ret.add("/js/dc.go.js");        // start the UI scripts
		}

		return ret;
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

		for (XElement config : ResourceHub.getResources().getConfig().getTagListDeep("Web")) {
			for (XElement route : config.selectAll("Route")) {
				if (host.equals(route.getAttribute("Name"))) {
					if (route.hasAttribute("RedirectPath"))
						return route.getAttribute("RedirectPath");

					if (!route.hasAttribute("ForceTls") && !route.hasAttribute("RedirectName"))
						continue;

					boolean tlsForce = Struct.objectToBoolean(route.getAttribute("ForceTls", "False"));
					String rname = route.getAttribute("RedirectName");

					boolean changeTls = ((ssl == null) && tlsForce);

					if (StringUtil.isNotEmpty(rname) || changeTls) {
						String path = ((ssl != null) || tlsForce) ? "https://" : "http://";

						path += StringUtil.isNotEmpty(rname) ? rname : host;

						// if forcing a switch, use another port
						path += changeTls ? ":" + route.getAttribute("TlsPort", defPort) : port;

						return path + orgpath;
					}
				}

				if (orgpath.equals(route.getAttribute("Path"))) {
					if (route.hasAttribute("RedirectPath"))
						return route.getAttribute("RedirectPath");
				}
			}

			if ((ssl == null) && Struct.objectToBoolean(config.getAttribute("ForceTls", "False")))
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
			WebFindResult wpath = this.webFindFilePath(path, view);

			if (wpath != null)
				return this.webPathToAdapter(view, wpath);

			// TODO not found file!!
			Logger.errorTr(150007);
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
			Logger.errorTr(150007);
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

		HtmlMode hmode = this.getHtmlMode();

		// /galleries and /files always processed by static output because these areas can be altered by Editors and Admins
		// only developers and sysadmins should make changes that can run server scripts
		if ((path.getNameCount() > 1) && ("galleries".equals(path.getName(0)) || "files".equals(path.getName(0)))) {
			ioa = new StaticOutputAdapter();
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
		/*
		else if (filename.endsWith(".gas")) {
			ioa = new GasOutputAdapter();
		}
		*/
		else {
			ioa = new StaticOutputAdapter();
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
		if (path.hasFileExtension())
			return WebFindResult.of(this.findSectionFile(sect, path.toString(), view), path);
		
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
			
			// TODO move this - we want to check all extensions at folder level then go up the path
			for (String ext : this.specialExtensions) {
				Path cfile = this.findSectionFile(sect, ppath.toString() + ext, view);
				
				if (cfile != null)
					return WebFindResult.of(cfile, ppath);
			}
			
			pdepth--;
		}
		
		Logger.errorTr(150007);
		return null;
	}

	@Override
	public Struct queryVariable(String name) {
		Struct res = super.queryVariable(name);

		if (res == null)
			res = this.getTenant().queryVariable(name);

		return res;
	}
}
