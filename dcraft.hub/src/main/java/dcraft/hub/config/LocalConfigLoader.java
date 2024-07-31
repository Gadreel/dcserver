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
package dcraft.hub.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.resource.*;
import dcraft.hub.resource.Package;
import dcraft.hub.resource.lib.Bundle;
import dcraft.locale.Dictionary;
import dcraft.locale.LocaleResource;
import dcraft.locale.LocaleUtil;
import dcraft.log.HubLog;
import dcraft.log.Logger;
import dcraft.schema.SchemaResource;
import dcraft.struct.Struct;
import dcraft.task.TaskContext;
import dcraft.util.ISettingsObfuscator;
import dcraft.util.MimeInfo;
import dcraft.util.StringUtil;
import dcraft.util.pgp.KeyRingCollection;
import dcraft.web.md.Configuration;
import dcraft.xml.XElement;
import dcraft.xml.XmlReader;

/**
 *
 * @author Andy
 *
 */
abstract public class LocalConfigLoader extends CoreLoaderWork {
	protected LocalConfigLoader() {
	}
	
	abstract public Path resolvePath(Path p);
	abstract public Path resolveRolePath(Path p);
	abstract public Path resolveNodePath(Path p);
	
	public void initResources(TaskContext tctx, ResourceTier resources) {
		ConfigResource configres = resources.getOrCreateTierConfig();
		
		// change to overrides if find config
		XElement logger = configres.getTag("Logger");
		
		// prepare the logger - use files, use custom log writer
		if (! HubLog.init(logger)) {
			Logger.error("Unable to initialize Logger");
			return;
		}
		
		tctx.withDebugLevel(HubLog.getGlobalLevel());
		
		Logger.trace("Start init resources work");

		if (Files.notExists(ApplicationHub.getDeploymentNodePath()) && ! "00001".equals(ApplicationHub.getNodeId())) {
			Logger.error("Missing node folder, node folder must be added.");
			return;
		}

		// -----------------------------------
		//   apply config minimally
		// -----------------------------------
		
		// TODO find the node role (typically in deployment.xml) from matrix - assign it to
		// roles may inherit another role - at the core there are these roles: local and remote
		// however we know sentintel (a type of Remote), and dev (a type of Local)

		//ApplicationHub.setRole(configres.getAttribute("Role", ApplicationHub.getRole()));
		
		// TODO make sure local or remote are set
		
		// TODO find the prod flag (typically in deployment.xml) from matrix - assign it to
		//ApplicationHub.setProduction(Struct.objectToBoolean(configres.getAttribute("IsProduction"), ApplicationHub.isProduction()));
		
		ApplicationHub.setProduction(Struct.objectToBoolean(configres.getAttribute("IsProduction"), ApplicationHub.isProduction()));
		
		// -----------------------------------
		//   load packages from local files
		// -----------------------------------
		
		PackageResource packres = resources.getOrCreateTierPackages();
		
		XElement packages = configres.getTagLocal("Packages");
		
		if (packages != null) {
			String packagepath = packages.getAttribute("Path", "./packages");
			
			Path pspath = Paths.get(packagepath);
			
			// TODO someday replace with FileStore system so we always load through FS
			packres.setTierPath(pspath);
			
			for (XElement pack : packages.selectAll("Package")) {
				String name = pack.getAttribute("Name");
				
				if (StringUtil.isEmpty(name))
					continue;
				
				Path ppath = pspath.resolve(name);
				
				if (! Files.exists(ppath))
					continue;
				
				Path pxml = ppath.resolve("package.xml");
				
				if (Files.exists(pxml)) {
					XElement xres = XmlReader.loadFile(pxml, false, true);
					
					if (xres == null)
						continue;
					
					Package hpackage = Package.of(name, xres, ppath);
					
					packres.addToTier(hpackage);
				}
			}
			
			// put packages into config resource in reverse order to the top package in list is last checked
			// thus allowing later package to override the root package
			for (Package hpackage : packres.getReverseTierList()) {
				configres.addTop(hpackage.getDefinition().find("Config"));
				
				Path spath = hpackage.getPath().resolve("script");
				
				if (Files.exists(spath))
					resources.getOrCreateTierScripts().withPath(spath);

				spath = hpackage.getPath().resolve("emails");

				if (Files.exists(spath))
					resources.getOrCreateTierScripts().withPath(spath);

				Path commpath = hpackage.getPath().resolve("communicate");

				if (Files.exists(commpath))
					resources.getOrCreateTierComm().withPath(commpath);

				Path docspath = hpackage.getPath().resolve("docs");

				if (Files.exists(docspath))
					resources.getOrCreateTierDoc().withPath(docspath);

				/*
				spath = hpackage.getPath().resolve("node-modules");

				if (Files.exists(spath))
					resources.getOrCreateTierNodes().withModule(spath);

				spath = hpackage.getPath().resolve("node");

				if (Files.exists(spath))
					resources.getOrCreateTierNodes().withScript(spath);

				 */
			}
		}
		
		Logger.trace("After packages");
		
		// make sure top level class loader is present
		ClassResource cr = this.getClasses(resources);
		
		if (cr == null) {
			Logger.error( "Unable to load lib file(s)");
			return;
		}
		
		// -----------------------------------
		//   make sure we have the correct Obfuscator
		// -----------------------------------
		
		XElement clockconfig = configres.getTagLocal("Clock");
		
		if (clockconfig != null) {
			String obclass = clockconfig.getAttribute("TimerClass");
			
			if (StringUtil.isNotEmpty(obclass)) {
				try {
					ISettingsObfuscator obfus = (ISettingsObfuscator) resources.getClassLoader().getInstance(obclass);
					
					obfus.load(clockconfig);
					
					ApplicationHub.getClock().setObfuscator(obfus);
				}
				catch (Exception x) {
					Logger.error("Unable to load custom Settings Obfuscator class: " + obclass, "Code", "207");
				}
			}
			else {
				ApplicationHub.getClock().getObfuscator().load(clockconfig);
			}
		}
		
		/* TODO
		Logger.debugTr(0, "Initializing package file store");
		
		if (! ApplicationHub.getPackages().init(config.getTagLocal("PackageFileStore"))) {
			Logger.error("Unable to initialize packages");
			return false;
		}
		*/
		
		for (XElement lel : configres.getTagListLocal("Formatters/Definition")) {
			resources.getOrCreateTierScripts().loadFormatter(lel);
		}
		
		Logger.trace( "Packages loaded: " + packres);
		
		// -----------------------------------
		//   load locale from local files
		// -----------------------------------
		
		LocaleResource lr = resources.getOrCreateTierLocale();
		
		lr.setDefaultChronology(configres.getAttribute("Chronology", "UTC"));
		
		lr.setDefaultLocale(LocaleUtil.normalizeCode(configres.getAttribute("Locale", "eng")));
		
		Logger.trace( "Loaded locale settings");
		
		// -----------------------------------
		//   load dict from local files
		// -----------------------------------
		
		Logger.trace( "Using project compiler to load schema and dictionary");
		
		lr.setLocalDictionary(this.getDictionary(resources));
		
		// -----------------------------------
		//   load schema from local files
		// -----------------------------------

		SchemaResource schemaman = this.getSchema(resources);
		
		if (schemaman == null) {
			Logger.error( "Unable to load schema file(s)");
			return;
		}
		
		// -----------------------------------
		//   load MIME
		// -----------------------------------
		
		MimeResource mimeres = resources.getOrCreateTierMime();
		
		for (XElement mimeinfo : configres.getTagListLocal("Mime/Definition")) {
			String mtype = mimeinfo.getAttribute("Type");
			String ext = mimeinfo.getAttribute("Ext");
			
			if (StringUtil.isNotEmpty(mtype) && StringUtil.isNotEmpty(ext)) {
				ext = ext.toLowerCase();
				mtype = mtype.toLowerCase();
				
				MimeInfo info = MimeInfo.create()
						.withExt(ext)
						.withMimeType(mtype)
						.withIcon(mimeinfo.getAttribute("Icon"))
						.withCompress(Struct.objectToBoolean(mimeinfo.getAttribute("Compress","false")));
				
				mimeres.with(info);
			}
		}
		
		// -----------------------------------
		//   load keyrings - TODO make more effecient, on demand?
		// -----------------------------------
	
		KeyRingResource keyres = resources.getOrCreateTierKeyRing();
		
		KeyRingCollection key1 = KeyRingCollection.load(this.resolvePath(Paths.get(".")), false);
		
		if (key1 != null)
			keyres.withKeys(key1);
		
		KeyRingCollection key2 = KeyRingCollection.load(this.resolveRolePath(Paths.get(".")), false);
		
		if (key2 != null)
			keyres.withKeys(key2);
		
		KeyRingCollection key3 = KeyRingCollection.load(this.resolveNodePath(Paths.get(".")), false);
		
		if (key3 != null)
			keyres.withKeys(key3);
		
		// -----------------------------------
		//   load certificates
		// -----------------------------------
		
		TrustResource trustres = resources.getOrCreateTierTrust();
		
		// TODO load trusted keys/prints

		// TODO load all certs, just match the passwords as
		
		for (XElement certinfo : configres.getTagListLocal("Certificate")) {
			String certname = certinfo.getAttribute("Name");
			
			if (StringUtil.isNotEmpty(certname)) {
				Path certpath = this.resolveNodePath(Paths.get(certname));
				
				if ((certpath == null) || Files.notExists(certpath))
					certpath = this.resolveRolePath(Paths.get(certname));
				
				if ((certpath == null) || Files.notExists(certpath))
					certpath = this.resolvePath(Paths.get(certname));
				
				if ((certpath == null) || Files.notExists(certpath)) {
					Logger.error("Unable to locate certificate: " + certname);
				}
				else {
					SslEntry entry = SslEntry.ofJks(trustres, certpath,
							certinfo.getAttribute("Password"), certinfo.getAttribute("PlainPassword"));
					
					if (entry == null) {
						Logger.error("Unable to load certificate: " + certname);
					}
					else {
						trustres.withSsl(entry, certinfo.getAttributeAsBooleanOrFalse("Default"));
					}
				}
			}
		}

		// -----------------------------------
		//   load markdown config
		// -----------------------------------
		
		// force create of the markdown resources
		Configuration mdc = resources.getOrCreateTierMarkdown().getUnsafeConfig();

		if (Logger.isTrace())
			Logger.trace("Default MD plugins: " + mdc.getPlugins().size());
		
		Logger.info( "Hub resources loaded");
	}
	
	public ClassResource getClasses(ResourceTier tier) {
		ClassResource cr = tier.getOrCreateTierClassLoader();
		
		Bundle bundle = new Bundle(this.getClass().getClassLoader());
		
		cr.setBundle(bundle);
		
		// load in package order so that later packages override the original
		for (Package pkg : tier.getOrCreateTierPackages().getTierList()) {
			Path sdir = pkg.getPath().resolve("lib");
			
			Logger.trace("Checking for libs in: " + sdir.toAbsolutePath());
			
			if (Files.exists(sdir)) {
				try {
					Files.walk(sdir).forEach(sf -> {
						if (sf.getFileName().toString().endsWith(".jar")) {
							Logger.trace("Loading lib: " + sf.toAbsolutePath());
							bundle.loadJarLibrary(sf);
						}
					});
				}
				catch (IOException x) {
					Logger.warn("Unabled to get folder listing: " + x);
				}
			}
		}
		
		return cr;
	}
	
	public SchemaResource getSchema(ResourceTier tier) {
		SchemaResource sm = tier.getOrCreateTierSchema();
		
		// load in package order so that later packages override the original
		for (Package pkg : tier.getOrCreateTierPackages().getTierList()) {
			Path sdir = pkg.getPath().resolve("schema");
			
			Logger.trace("Checking for schemas in: " + sdir.toAbsolutePath());
			
			if (Files.exists(sdir)) {
				// TODO make sure that we get in canonical order
				
				try {
					Files.walk(sdir).forEach(sf -> {
						if (sf.getFileName().toString().endsWith(".xml")) {
							Logger.trace("Loading schema: " + sf.toAbsolutePath());
							sm.loadSchema(sf);
						}
					});
				}
				catch (IOException x) {
					Logger.warn("Unabled to get folder listing: " + x);
				}
			}
		}
		
		sm.compile();
		
		return sm;
	}
	
	public Dictionary getDictionary(ResourceTier tier) {
		Dictionary loc = Dictionary.create();
		
		// load in package order so that later packages override the original
		for (Package pkg : tier.getOrCreateTierPackages().getTierList()) {
			Path sdir = pkg.getPath().resolve("dictionary");
			
			Logger.trace("Checking for dictionary in: " + sdir.toAbsolutePath());
			
			if (Files.exists(sdir)) {
				// TODO make sure that we get in canonical order
				
				try {
					Files.walk(sdir).forEach(sf -> {
						if (sf.getFileName().toString().endsWith(".xml")) {
							Logger.trace("Loading dictionary: " + sf.toAbsolutePath());
							loc.load(sf);
						}
					});
				}
				catch (IOException x) {
					Logger.warn("Unabled to get folder listing: " + x);
				}
			}
		}

		Logger.trace("Finished loading dictionaries");
		
		return loc;
	}
	
	public void addConfigIfPresent(ConfigResource configres, Path path, boolean top) {
		// this is fine, not all config
		if (path == null)
			return;
		
		Logger.trace("Resolving config xml file: " + path.getFileName());
		
		//Path fshared = this.resolvePath(Paths.get(name));
		
		if (Files.exists(path)) {
			XElement cel = XmlReader.loadFile(path, false, true);
			
			if (cel == null) {
				Logger.error("Unable to load config file, expected: " + path);
			}
			else {
				Logger.trace("Loaded config xml file: " + path);

				if (top)
					configres.addTop(cel);
				else
					configres.add(cel);
			}
		}
	}
}
