package dcraft.tenant;

import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchEvent.Kind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dcraft.filestore.local.LocalStore;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.IOperationObserver;
import dcraft.log.Logger;
import dcraft.task.TaskHub;
import dcraft.tenant.work.TenantFactory;
import dcraft.util.IOUtil;
import dcraft.util.StringUtil;
import dcraft.util.io.IFileWatcher;
import dcraft.util.web.DomainNameMapping;

public class TenantHub {
	// domain tracking
	static protected DomainNameMapping<Tenant> domainmap = new DomainNameMapping<>();
	static protected Map<String, Tenant> aliasmap = new HashMap<>();

	// file tracking
	static protected LocalStore filestore = null;
	static protected WatchKey queuekey = null;
	
	
	static public Collection<Tenant> getTenants() {
		return TenantHub.aliasmap.values();
	}
	
	public static void setFileStore(LocalStore v) {
		TenantHub.filestore = v;
		
		v.connect(null, null);
		
		v.withObserver(new IFileWatcher() {
			@Override
			public void fireFolderEvent(Path fname, WatchEvent.Kind<Path> kind) {
				Logger.info("Tenant file changed: " + kind + " name: " + v.relativize(fname));
			}
		});
	}
	
	static public LocalStore getFileStore() {
		return TenantHub.filestore;
	}
	
	static public Tenant resolveTenant(String domain) {
		if (StringUtil.isEmpty(domain)) 
			return null;

		// try lookup of domain name
		Tenant di = TenantHub.domainmap.get(domain);
		
		if (di != null)
			return di;

		// if not an domain name then try lookup of domain alias
		di = TenantHub.aliasmap.get(domain);
		
		if (di != null)
			return di;
		
		return null;
	}
	
	static public Site resolveSite(String domain) {
		if (StringUtil.isEmpty(domain))
			return null;
		
		// try lookup of domain name
		Tenant di = TenantHub.domainmap.get(domain);
		
		if (di != null)
			return di.resolveSite(domain);
		
		return null;
	}
	
	static public void setTenants(Tenant... tenants) {
		TenantHub.setTenants(Arrays.asList(tenants));
	}
	
	static public void setTenants(List<Tenant> tenants) {
		TaskHub.submit(TenantFactory.updateTenants(false, tenants, null, true));
	}
	
	static public void loadAll() {
		TaskHub.submit(TenantFactory.updateTenants(false, new ArrayList<>(TenantHub.aliasmap.values()), null, false));
	}
	
	static public void loadTenants(Tenant... tenants) {
		TaskHub.submit(TenantFactory.updateTenants(true, Arrays.asList(tenants), null, false));
	}

	static public void removeTenants(String... alias) {
		// go through the work to get the cleanup 
		TaskHub.submit(TenantFactory.updateTenants(false, null, Arrays.asList(alias), true));
	}

	static public void loadDeafultTenant() {
		Collection<Tenant> newtenants = new ArrayList<>();
		
		Tenant root = Tenant.of("root")
				.withObfuscator(ApplicationHub.getClock().getObfuscator());
		
		root
				.withTitle("Root Tenant");
		
		root.internalAddSite(Site.of(root, "root")
			.withDomain("root")
			.withDomain("localhost"));
		
		newtenants.add(root);
		
		TenantHub.internalSwitchTenants(newtenants);
	}

	/*
	static public void loadFileQueue() {
		Path fqueue = TenantHub.getFileStore().resolvePath("/_fqueue");
		
		try {
			Files.createDirectories(fqueue);
			
			TenantHub.queuekey = ApplicationHub.registerFileWatcher(new IFileWatcher() {
				@Override
				public void fireFolderEvent(Path fname, WatchEvent.Kind<Path> kind) {
					if (fname.toString().equals("CMD") && (kind != ENTRY_DELETE)) {
						// TODO use a root task to process this - process in separate class
						Path fpath = TenantHub.filestore.resolvePath("/_fqueue/" + fname);
						
						try {
							String msg = StringUtil.stripWhitespace(IOUtil.readEntireFile(fpath).toString());
							
							Files.delete(fpath);
							
							String cmd = msg.substring(0, msg.indexOf('='));
							String exp = msg.substring(msg.indexOf('=') + 1);
							
							// TODO need to invoke the Tenants service to have it reload the tenant(s)
							// this doesn't get the new config
							
							if (cmd.equals("REFRESH")) {
								// clear all cache
								if (exp.equals("_core")) {
									Logger.info("Clearing the resource and tenant filestores cache");
									
									TenantHub.updateAll();
								}
								// reload just one tenant
								else {
									Tenant t = TenantHub.resolveTenant(exp);
									
									// TODO if we later support a syntax for multiple then call updateTenants only once
									if (t != null)
										TenantHub.updateTenants(t);
								}
							}
						}
						catch (Exception x) {
							System.out.println("failed fqueue file: " + fname);
						}
					}
				}
			}, fqueue);
		}
		catch (Exception x) {
			Logger.error("Failed to start tenant fqueue: " + x);
		}
	}

	 */
	
	static public void stopFileQueue() {
		if (TenantHub.queuekey != null) {
			TenantHub.queuekey.cancel();
			TenantHub.queuekey = null;
		}
	}

	// don't call this, internal only
	// this is the entire list of tenants, all old are replaced
	public static void internalSwitchTenants(Collection<Tenant> newtenants) {
		Map<String, Tenant> newaliasmap = new HashMap<>();

		for (Tenant ten : newtenants) {
			newaliasmap.put(ten.getAlias(), ten);
		}
		
		DomainNameMapping<Tenant> newdomainmap = new DomainNameMapping<>();

		for (Tenant ten : newaliasmap.values()) {
			for (String domain : ten.getDomains()) {
				newdomainmap.add(domain, ten);
			}
		}

		// there will be an extremely brief period of time where these two fields
		// may be out of alignment but it doesn't truly hurt anything, at least not
		// enough to make it worth the overhead of read and write locks
		TenantHub.aliasmap = newaliasmap;
		TenantHub.domainmap = newdomainmap;
	}
}
