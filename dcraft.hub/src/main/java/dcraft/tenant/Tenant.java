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
package dcraft.tenant;

import java.nio.file.Path;
import java.util.*;

import dcraft.filestore.local.LocalStore;
import dcraft.filestore.local.LocalStoreFile;
import dcraft.filevault.GalleryVault;
import dcraft.filevault.Vault;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.resource.ResourceTier;
import dcraft.util.ISettingsObfuscator;
import dcraft.util.StringUtil;
import dcraft.util.groovy.GCompClassLoader;
import dcraft.xml.XElement;

public class Tenant extends Base {
	public static Tenant of(String alias) {
		Tenant ten = new Tenant();
		ten.with("Alias", alias);
		return ten;
	}
	
	// data, data types, data protection/crypto, services, trust managers, watchers and schedules
	// are at Tenant level, not Site level
	protected ISettingsObfuscator obfuscator = null;
	//protected TenantWatcherAdapter watcher = null;

	protected Map<String, Site> sites = new HashMap<>();
	protected Map<String, Site> domainsites = new HashMap<>();
	
	protected ResourceTier config = null;
	
	protected Map<String, Vault> vaults = new HashMap<>();
	protected Vault tenantfiles = null;
	
	/* TODO moved to config resource
	protected List<ISchedule> schedulenodes = new ArrayList<>();
	protected TrustManager[] trustManagers = new TrustManager[1];
	*/
	
	/* TODO move to CoreService
	protected List<WatchKey> watchkeys = new ArrayList<>();
	*/
	
	public ResourceTier getResources() {
		if (this.config != null)
			return this.config;
		
		return ResourceHub.getTopResources();
	}
	
	public ResourceTier getTierResources() {
		return this.config;
	}
	
	public ResourceTier getResourcesOrCreate(ResourceTier parent) {
		if (this.config == null)
			this.config = ResourceTier.tier(parent);
		
		return this.config;
	}
	
	public Set<String> getDomains() {
		return this.domainsites.keySet();
	}
	
	public Collection<Site> getSites() {
		return this.sites.values();
	}
	
	public Tenant withObfuscator(ISettingsObfuscator v) {
		this.obfuscator = v;
		return this;
	}
	
	public ISettingsObfuscator getObfuscator() {
		return this.obfuscator;
	}
	
	/* TODO moved to config resource
	public TrustManager[] getTrustManagers() {
		return this.trustManagers;
	}
	*/

	@Override
	public Path getPath() {
		LocalStore fs = TenantHub.getFileStore();
		
		if (fs == null)
			return null;
		
		return fs.resolvePath(this.getAlias());
	}
	
	public LocalStoreFile resolvePathToStore(String path) {
		LocalStore fs = TenantHub.getFileStore();
		
		if (fs == null)
			return null;
		
		if (StringUtil.isEmpty(path))
			return null;
		
		if (path.charAt(0) == '/')
			return fs.resolvePathToStore(this.getAlias() + path);
		
		return fs.resolvePathToStore(this.getAlias() + "/" + path);
	}
	
	public Site getRootSite() {
		return this.sites.get("root");
	}
	
	public Site resolveSite(String domain) {
		if (StringUtil.isEmpty(domain)) 
			return null;
		
		// if this is a site alias then return it
		Site di = this.sites.get(domain);
		
		if (di != null)
			return di;

		// if not an alias then try lookup of domain name
		di = this.domainsites.get(domain);
		
		if (di != null)
			return di;
		
		// root is default
		return this.sites.get("root");
	}

	// don't call, for Tenant Services
	public void internalAddSite(Site v) {
		this.sites.put(v.getAlias(), v);
		
		for (String domain : v.getDomains())
			this.domainsites.put(domain, v);
	}
	
	protected Tenant() {
	}

	public Collection<Vault> getVaults() throws OperatingContextException {
		List<XElement> vaults = this.getResources().getConfig().getTagListDeep("Vaults/Tenant");

		for (XElement bucket : vaults) {
			String alias = bucket.getAttribute("Id");

			if (StringUtil.isEmpty(alias) || this.vaults.containsKey(alias))
				continue;

			Vault b = Vault.of(this.getRootSite(), bucket);

			if (b != null)
				this.vaults.put(alias, b);
		}

		return this.vaults.values();
	}

	@Override
	public Vault getVault(String alias) throws OperatingContextException {
		// like tenant database - this is shared data
		Vault b = this.vaults.get(alias);
		
		if (b == null) {
			XElement bucket = this.getResources().getConfig().findId("Vaults/Tenant", alias);

			if (bucket == null)
				return null;

			b = Vault.of(this.getRootSite(), bucket);

			if (b != null)
				this.vaults.put(alias, b);
		}
		
		return b;
	}
	
	/* TODO move to CoreService
	public void watchSettingsChange(Path path) {
		WatchKey key = ApplicationHub.registerFileWatcher(this, path);

		if (key != null)
			this.watchkeys.add(key);
	}

	@Override
	public void fireFolderEvent(Path fname, WatchEvent.Kind<Path> kind) {
		// TODO request reload from CoreServices
	}
	*/

	/* TODO move to CoreService
	public void registerService(IService service) {
		this.registered.put(service.serviceName(), service);
		
		ServiceRouter r = new ServiceRouter(service.serviceName());
		r.indexLocal();
		
		this.routers.put(service.serviceName(), r);
	}
	*/
	
	@Override
	public String toString() {
		return this.getTitle();
	}
}
