package dcraft.tenant.work;

import dcraft.db.Constants;
import dcraft.db.request.query.LoadRecordRequest;
import dcraft.db.request.query.SelectFields;
import dcraft.filestore.CommonPath;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.resource.*;
import dcraft.hub.resource.Package;
import dcraft.locale.Dictionary;
import dcraft.locale.LocaleDefinition;
import dcraft.locale.LocaleUtil;
import dcraft.log.Logger;
import dcraft.schema.SchemaResource;
import dcraft.service.IService;
import dcraft.service.ServiceHub;
import dcraft.service.ServiceResource;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.StringStruct;
import dcraft.task.*;
import dcraft.tenant.*;
import dcraft.tool.certs.CertUtil;
import dcraft.util.ISettingsObfuscator;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.util.pgp.KeyRingCollection;
import dcraft.web.*;
import dcraft.web.adapter.ManifestOutputAdapter;
import dcraft.web.adapter.ScriptCacheOutputAdapter;
import dcraft.web.adapter.StyleCacheOutputAdapter;
import dcraft.web.adapter.WizardOutputAdapter;
import dcraft.xml.XElement;
import dcraft.xml.XmlReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

public class PrepWork extends StateWork {
	protected Tenant tenant = null;
	
	public void setTenant(Tenant v) {
		this.tenant = v;
	}

	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this
				.withStep(StateWorkStep.of("Config Tenant", this::configTenant))
				.withStep(StateWorkStep.of("Config Tenant From DB", this::dbConfigTenant))
				.withStep(StateWorkStep.of("Load Obfuscator", this::loadObfuscator))
				.withStep(StateWorkStep.of("Load PGP Keys", this::loadPGPKeys))
				.withStep(StateWorkStep.of("Load Packages", this::loadPackages))		// before schema to pickup new schema
				.withStep(StateWorkStep.of("Load Schema", this::loadSchema))
				.withStep(StateWorkStep.of("Load Script", this::loadScript))
				.withStep(StateWorkStep.of("Load Services", this::loadServices))
				.withStep(StateWorkStep.of("Load Sites", this::loadSites))
				//.withStep(StateWorkStep.of("Load Schedules", this::loadSchedule))
		;
	}

	public StateWorkStep configTenant(TaskContext trun) throws OperatingContextException {
		if (Logger.isDebug())
			Logger.debug("Starting tenant load config for: " + this.tenant.getAlias());
		
		ResourceTier resources = this.tenant.getTierResources();

		if (resources == null) {
			Logger.error("Tenant resource tier must be set before running tenant prep.");
			return StateWorkStep.STOP;
		}

		ConfigResource config = resources.getOrCreateTierConfig();

		// --- load tenant level config, if any ---

		Path cpath = this.tenant.resolvePath("config");

		if ((cpath != null) && Files.exists(cpath)) {
			Path cspath = cpath.resolve("shared.xml");

			if (Files.exists(cspath)) {
				XElement xres = XmlReader.loadFile(cspath, true, true);

				if (xres != null)
					config.add(xres);
			}
		}

		this.tenant.withTitle(config.getAttribute("Title"));

		return StateWorkStep.NEXT;
	}
	
	public StateWorkStep dbConfigTenant(TaskContext trun) throws OperatingContextException {
		if (Logger.isDebug())
			Logger.debug("Starting tenant load db config for: " + this.tenant.getAlias());
		
		if (! ResourceHub.getResources().getDatabases().hasDefaultDatabase())
			return StateWorkStep.NEXT;
		
		ServiceHub.call(LoadRecordRequest.of("dcTenant")
				.withId(Constants.DB_GLOBAL_ROOT_RECORD)
				.withSelect(SelectFields.select()
						.with("dcConfig")
				)
				//.withForTenant(this.tenant.getAlias())		// needed here because tenants are not in tenanthub
				.toServiceRequest()
				.withOutcome(new OperationOutcomeStruct() {
					@Override
					public void callback(Struct result) throws OperatingContextException {
						if (! this.hasErrors()) {
							RecordStruct rec = Struct.objectToRecord(result);
							
							if (rec != null) {
								String configstr = rec.getFieldAsString("dcConfig");
								
								XElement xml = XmlReader.parse(configstr, true, true);
								
								if (xml == null)
									Logger.error("Error parsing db config");
								else
									PrepWork.this.tenant.getResources().getConfig().add(xml);
							}
							else {
								Logger.error("Missing db config, it is required.");
							}
						}
						
						PrepWork.this.resumeNextStep(trun);
					}
				}));
		
		return StateWorkStep.WAIT;
	}

	// TODO make sure tenant class loaders are set before calling this
	public StateWorkStep loadObfuscator(TaskContext trun) throws OperatingContextException {
		if (Logger.isDebug())
			Logger.debug("Starting tenant load clock for: " + this.tenant.getAlias());
		
		ResourceTier resources = this.tenant.getTierResources();

		ConfigResource config = resources.getOrCreateTierConfig();

		XElement clock = config.getTag("Clock");
		
		XElement appclock = ResourceHub.getTopResources().getConfig().getTag("Clock");
		
		if ((clock != null) && (clock != appclock) && clock.hasNotEmptyAttribute("TimerClass") && clock.hasNotEmptyAttribute("Feed")) {
			// load tenant level obfuscator
			String obclass = clock.getAttribute("TimerClass");
			
			ISettingsObfuscator obfuscator = null;
			
			try {
				obfuscator = (ISettingsObfuscator) resources.getClassLoader().getInstance(obclass);
			}
			catch (Exception x) {
				Logger.error("Bad Settings Obfuscator");
				return StateWorkStep.STOP;
			}

			// tenant always borrows from app
			clock.withAttribute("Id", appclock.getAttribute("Id"));
			
			obfuscator.load(clock);
			
			this.tenant.withObfuscator(obfuscator);
		}
		else {
			// TODO error?
			// timerclass required
		}
		
		return StateWorkStep.NEXT;
	}

	public StateWorkStep loadPGPKeys(TaskContext trun) throws OperatingContextException {
		if (Logger.isDebug())
			Logger.debug("Starting tenant load keys for: " + this.tenant.getAlias());
		
		ResourceTier resources = this.tenant.getTierResources();

		Path cpath = this.tenant.resolvePath("config");

		KeyRingCollection key1 = KeyRingCollection.load(cpath, false);

		if (key1 != null) {
			KeyRingResource keyres = resources.getOrCreateTierKeyRing();

			keyres.withKeys(key1);

			//Logger.info("Found secret keys: " + key1.getSecretKeys().size());
		}

		// TODO if no keys then don't run this tenant
		// TODO create a way for a tenant to be removed from the list during prep - either due to offline or missing config

		return StateWorkStep.NEXT;
	}

	public StateWorkStep loadSchema(TaskContext trun) throws OperatingContextException {
		if (Logger.isDebug())
			Logger.debug("Starting tenant load schema for: " + this.tenant.getAlias());
		
		ResourceTier resources = this.tenant.getTierResources();

		Path cpath = this.tenant.resolvePath("config");

		// --- load tenant level schema, if any ---
		// we reload and recompile all data types per tenant so that the tenant's overrides on data types can take effect naturally
		// does mean extra memory is used, but is very efficient use of data typing

		try {
			// TODO before creating a schema resource and duplicating all the data types
			// first consider checking if there are any schema files for the tenant - if not then skip all this

			// block the parent so that type lookups don't get doubled
			SchemaResource sm = resources.getOrCreateTierSchema().withBlockParent();

			// server level packages
			// load in package order so that later packages override the original
			for (XElement def : ResourceHub.getTopResources().getSchema().getDefinitions()) {
				sm.loadSchema("inherit-top", def);		// TODO this could be better named / organized
			}

			// tenant level packages
			// load in package order so that later packages override the original
			for (Package pkg : resources.getOrCreateTierPackages().getTierList()) {
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
						Logger.warn("Unable to get folder listing: " + x);
					}
				}
			}

			// tenant level custom schema
			Path shpath = cpath.resolve("schema.xml");

			if (Files.exists(shpath)) {
				Logger.trace("Loading schema: " + shpath.toAbsolutePath());
				sm.loadSchema(shpath);
			}
			
			// tenant level custom schema - extras
			Path shsubpath = cpath.resolve("schema");

			if (Files.exists(shsubpath)) {
				try (Stream<Path> strm = Files.list(shsubpath)) {
					strm.forEach(entry -> {
						Logger.trace("Loading schema: " + entry.toAbsolutePath());
						sm.loadSchema(entry);
					});
				}
			}

			sm.compile();
		}
		catch (Exception x) {
			Logger.error("Error loading schema: " + x);
		}

		return StateWorkStep.NEXT;
	}

	public StateWorkStep loadPackages(TaskContext trun) throws OperatingContextException {
		if (Logger.isDebug())
			Logger.debug("Starting tenant load packages for: " + this.tenant.getAlias());
		
		ResourceTier resources = this.tenant.getTierResources();

		ConfigResource config = resources.getOrCreateTierConfig();

		// -----------------------------------
		//   load packages from local files
		// -----------------------------------

		XElement tpackages = config.getTagLocal("Packages");

		if (tpackages != null) {
			PackageResource packres = tenant.getResources().getOrCreateTierPackages();

			String packagepath = tpackages.getAttribute("Path", "./packages");

			Path pspath = Paths.get(packagepath);

			// TODO someday replace with FileStore system so we always load through FS
			packres.setTierPath(pspath);

			for (XElement pack : tpackages.selectAll("Package")) {
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
				config.addTop(hpackage.getDefinition().find("Config"));
			}

			// see next step
			//this.getSchema(tenant.getResources());
		}

		return StateWorkStep.NEXT;
	}

	public StateWorkStep loadScript(TaskContext trun) throws OperatingContextException {
		if (Logger.isDebug())
			Logger.debug("Starting tenant load script for: " + this.tenant.getAlias());
		
		ResourceTier resources = this.tenant.getTierResources();
		
		// --- load tenant level packages scripts
		
		// load in package order so that later packages override the original
		for (Package pkg : resources.getOrCreateTierPackages().getReverseTierList()) {
			Path spath = pkg.getPath().resolve("script");
			
			if (Files.exists(spath))
				resources.getOrCreateTierScripts().withPath(spath);

			spath = pkg.getPath().resolve("email");

			if (Files.exists(spath))
				resources.getOrCreateTierScripts().withPath(spath);

			spath = pkg.getPath().resolve("node-modules");

			if (Files.exists(spath))
				resources.getOrCreateTierNodes().withModule(spath);

			spath = pkg.getPath().resolve("node");

			if (Files.exists(spath))
				resources.getOrCreateTierNodes().withScript(spath);
		}

		// --- load tenant level script paths, if any ---

		Path spath = this.tenant.resolvePath("script");

		if (Files.exists(spath))
			resources.getOrCreateTierScripts().withPath(spath);

		spath = this.tenant.resolvePath("emails");

		if (Files.exists(spath))
			resources.getOrCreateTierScripts().withPath(spath);

		spath = this.tenant.resolvePath("node-modules");

		if (Files.exists(spath))
			resources.getOrCreateTierNodes().withModule(spath);

		spath = this.tenant.resolvePath("node");

		if (Files.exists(spath))
			resources.getOrCreateTierNodes().withScript(spath);

		ConfigResource config = resources.getOrCreateTierConfig();
		
		for (XElement lel : config.getTagListLocal("Formatters/Definition")) {
			resources.getOrCreateTierScripts().loadFormatter(lel);
		}
		
		return StateWorkStep.NEXT;
	}

	public StateWorkStep loadServices(TaskContext trun) throws OperatingContextException {
		if (Logger.isDebug())
			Logger.debug("Starting tenant load script for: " + this.tenant.getAlias());

		ResourceTier resources = this.tenant.getTierResources();

		ConfigResource config = resources.getOrCreateTierConfig();

		List<XElement> services = config.getTagListLocal("Service");

		if (services.size() > 0) {
			// TODO make sure services are stopped when site/tenant reload

			ServiceResource srres = resources.getOrCreateTierServices();

			for (XElement el : services) {
				try {
					String name = el.getAttribute("Name");

					if (StringUtil.isNotEmpty(name)) {
						IService srv = (IService) resources.getClassLoader().getInstance(el.getAttribute("RunClass", "dcraft.service.BaseDataService"));

						if (srv != null) {
							srv.init(name, el, resources);
							srres.registerTierService(name, srv);
						}
					}
				} catch (Exception x) {
					Logger.error("Unable to load serivce: " + el);
				}
			}
		}

		return StateWorkStep.NEXT;
	}

	public StateWorkStep loadSites(TaskContext trun) throws OperatingContextException {
		if (Logger.isDebug())
			Logger.debug("Starting tenant load sites for: " + this.tenant.getAlias());
		
		ResourceTier resources = this.tenant.getTierResources();

		ConfigResource config = resources.getOrCreateTierConfig();

		Path cpath = this.tenant.resolvePath("config");

		// -----------------------------------

		// TODO see below and insert some of that in here
		
		// prep sites
		
		List<XElement> sites = config.getTagListDeep("Site");

		if (sites.size() == 0)
			sites.add(XElement.tag("Site").withAttribute("Name", "root"));
		
		for (XElement pel :  sites) {
			String sname = pel.getAttribute("Name");
			
			if (StringUtil.isNotEmpty(sname)) {
				Site site = Site.of(this.tenant, sname);
				
				ConfigResource sconfig = site.getResourcesOrCreate(resources).getOrCreateTierConfig();

				sconfig.add(pel);

				Path scpath = site.resolvePath("config");
				
				if ((scpath != null) && Files.exists(scpath)) {
					Path scspath = scpath.resolve("config.xml");
					
					if (Files.exists(scspath)) {
						XElement xres = XmlReader.loadFile(scspath, true, true);
						
						if (xres != null)
							sconfig.add(xres);
					}
				}
				
				site.withTitle(sconfig.getAttribute("Title"));
				
				// --- load site level dictionary, if any ---
				
				try {
					Path dpath = scpath.resolve("dictionary.xml");
					
					if (Files.exists(dpath)) {
						Dictionary dict = Dictionary.create();
						
						Logger.trace("Loading dictionary: " + dpath.toAbsolutePath());
						dict.load(dpath);
						
						site.getResourcesOrCreate(resources).getOrCreateTierLocale().setLocalDictionary(dict);
					}
				}
				catch (Exception x) {
					Logger.error("Error loading dictionary: " + x);
				}
				
				// these settings are valid for root and sub sites
				
				for (XElement lel : sconfig.getTagListLocal("Locale")) {
					String lname = LocaleUtil.normalizeCode(lel.getAttribute("Name"));
					
					if (StringUtil.isEmpty(lname))
						continue;
					
					LocaleDefinition def = site.getResourcesOrCreate(resources).getOrCreateTierLocale().buildLocaleDefinition(lname);
					
					site.getResourcesOrCreate(resources).getOrCreateTierLocale().addLocaleDefinition(def);
					
					for (XElement del : lel.selectAll("Domain")) {
						String dname = del.getAttribute("Name");
						
						if (StringUtil.isNotEmpty(dname))
							site.withLocaleDomain(dname, def);
					}
				}
			
				site.getResourcesOrCreate(resources).getOrCreateTierLocale().setDefaultChronology(sconfig.getAttribute("Chronology"));
				site.getResourcesOrCreate(resources).getOrCreateTierLocale().setDefaultLocale(LocaleUtil.normalizeCode(sconfig.getAttribute("Locale")));
				
				// this setting is only valid for sub sites
				if (! site.isRoot()) {
					String itype = sconfig.getAttribute("Integration", "Files");
					
					try {
						site.withIntegration(SiteIntegration.valueOf(itype));
					}
					catch (Exception x) {
						site.withIntegration(SiteIntegration.Files);
					}
					
					// --- load tenant level script paths, if any ---
					
					Path spath = site.resolvePath("script");
					
					if (Files.exists(spath))
						site.getResourcesOrCreate(resources).getOrCreateTierScripts().withPath(spath);

					spath = site.resolvePath("emails");

					if (Files.exists(spath))
						site.getResourcesOrCreate(resources).getOrCreateTierScripts().withPath(spath);

					spath = site.resolvePath("node");

					if (Files.exists(spath))
						resources.getOrCreateTierNodes().withScript(spath);
				}
				
				for (XElement del : sconfig.getTagListLocal("Domain")) {
					String dname = del.getAttribute("Name");
					
					if (StringUtil.isEmpty(dname))
						continue;
					
					site.withDomain(dname);
				}
				
				// -----------------------------------
				//   load packages from local files
				// -----------------------------------
				
				XElement spackages = sconfig.getTagLocal("Packages");
				
				if (spackages != null) {
					PackageResource packres = site.getResourcesOrCreate(resources).getOrCreateTierPackages();
					
					String packagepath = spackages.getAttribute("Path", "./packages");
					
					Path pspath = Paths.get(packagepath);
					
					// TODO someday replace with FileStore system so we always load through FS
					packres.setTierPath(pspath);
					
					for (XElement pack : spackages.selectAll("Package")) {
						String name = pack.getAttribute("Name");
						
						if (StringUtil.isEmpty(name))
							continue;
						
						Path ppath = pspath.resolve(name);
						
						if (!Files.exists(ppath))
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
						sconfig.addTop(hpackage.getDefinition().find("Config"));
					}
				}
				
				// sites are not allowed their own schema, not even from a package
				//this.getSchema(site.getResourcesOrCreate(resources));

				for (XElement lel : sconfig.getTagListLocal("Variables/Var")) {
					String lname = lel.getAttribute("Name");

					if (StringUtil.isEmpty(lname))
						continue;

					if (lel.hasNotEmptyAttribute("Value")) {
						site.addVariable(lname, StringStruct.of(lel.getAttribute("Value")));
					}
					else {
						ListStruct varlist = ListStruct.list();

						for (XElement item : lel.selectAll("Item")) {
							if (item.hasNotEmptyAttribute("Value")) {
								varlist.with(StringStruct.of(item.getAttribute("Value")));
							}
						}

						if (! varlist.isEmpty())
							site.addVariable(lname, varlist);
					}
				}

				if (site.queryVariable("SiteCopyright") == null)
					site.addVariable("SiteCopyright", StringStruct.of(ZonedDateTime.now().getYear() + ""));

				// TODO load trusted keys/prints

				// TODO load all certs, just match the passwords as

				CertUtil.loadTierCerts(scpath, site.getTierResources());

				// TODO make sure services are stopped when site/tenant reload
				
				ServiceResource srres = site.getResourcesOrCreate(resources).getOrCreateTierServices();
				
				for (XElement el : sconfig.getTagListLocal("Service")) {
					try {
						String name = el.getAttribute("Name");
						
						if (StringUtil.isNotEmpty(name)) {
							IService srv = (IService) site.getResources().getClassLoader().getInstance(el.getAttribute("RunClass", "dcraft.service.BaseDataService"));
							
							if (srv != null) {
								srv.init(name, el, site.getResources());
								srres.registerTierService(name, srv);
							}
						}
					}
					catch (Exception x) {
						Logger.error("Unable to load serivce: " + el);
					}
				}
				
				for (XElement lel : sconfig.getTagListLocal("Formatters/Definition")) {
					site.getResourcesOrCreate(resources).getOrCreateTierScripts().loadFormatter(lel);
				}
				
				this.loadWeb(site, sconfig);
				
				this.tenant.internalAddSite(site);
			}
		}

		return StateWorkStep.NEXT;
	}
	
	/* doesn't work because tenants are not loaded yet
	public StateWorkStep loadSchedule(TaskContext trun) throws OperatingContextException {
		Task stask = Task.ofContext(OperationContext.context(UserContext.rootUser(this.tenant.getAlias(),"root"), trun.getController()))
				.withTitle("Load tenant schedule: " + this.tenant.getAlias())
				.withTopic(WorkTopic.DEFAULT)
				.withNextId("START")
				.withWork(new IWork() {
					@Override
					public void run(TaskContext taskctx) throws OperatingContextException {
						if (Logger.isDebug())
							Logger.debug("Starting tenant load schedule for: " + PrepWork.this.tenant.getAlias());
						
						List<XElement> schedules = PrepWork.this.tenant.getAlias().equals("root")
								? ResourceHub.getResources().getConfig().getTagListDeep("Schedules/*")
								: ResourceHub.getResources().getConfig().getTagListLocal("Schedules/*");
						
						for (XElement schedule : schedules) {
							String sfor = schedule.getAttribute("For", "Production,Test");
							
							if (! ApplicationHub.isProduction() && ! sfor.contains("Test"))
								continue;
							
							if (ApplicationHub.isProduction() && ! sfor.contains("Production"))
								continue;
							
							Logger.info("- add schedule: " + schedule.getAttribute("Title"));
							
							if ("CommonSchedule".equals(schedule.getName())) {
								Task schcontext = Task.ofHubRoot()
										.withTitle("Scheduled run: " + schedule.getAttribute("Title"))
										.withTopic("Batch")		// TODO need to build batch into the system
										.withNextId("SCHEDULE");
								
								CommonSchedule sched = CommonSchedule.of(schcontext.freezeToRecord(), null);
								
								sched.init(schedule);
								
								// TODO may not need this
								// sched.setTenantId(this.getId());
								
								Logger.info("- prepped schedule: " + schedule.getAttribute("Title") + " next run " + Instant.ofEpochMilli(sched.when()));
								
								// TODO record these for cancel and reload
								// this.schedulenodes.add(sched);
								
								ScheduleHub.addNode(sched);
							}
							else {
								Logger.error("- could not prep schedule: " + schedule.getAttribute("Title") + " not a common schedule");
							}
						}
					}
				});
		
		TaskHub.submit(stask);
		
		return StateWorkStep.NEXT;		// we don't care if the task ends
	}
	*/
	
	// TODO set globals, set package list
	protected void loadWeb(Site site, ConfigResource sconfig) {
		XElement webconfig =  sconfig.getTag("Web");

		// collect a list of the packages names enabled for this domain
		//HashSet<String> packagenames = new HashSet<>();
		
		site.setWebVersion(DateTimeFormatter.ofPattern("yyMMddHHmm").format(TimeUtil.now()));
		
		if (Logger.isDebug())
			Logger.debug("Checking web domain settings from domain: " + site.getTenant().getAlias() +
					" : " + site.getAlias());
		
		site.setScriptCache(ApplicationHub.isProduction());
		
		if (webconfig != null) {
			String indexurl = webconfig.getAttribute("IndexUrl");
			
			if (StringUtil.isNotEmpty(indexurl))
				site.with("IndexUrl", indexurl.substring(0, indexurl.length() - 1));
			
			if (webconfig.hasAttribute("HtmlMode")) {
				try {
					site.setHtmlMode(HtmlMode.valueOf(webconfig.getAttribute("HtmlMode")));
				}
				catch (Exception x) {
					Logger.error("Unknown HTML Mode: " + webconfig.getAttribute("HtmlMode"));
				}
			}
			
			if (webconfig.hasAttribute("HomePath"))
				site.setHomePath(new CommonPath(webconfig.getAttribute("HomePath")));
			else if ((site.getHtmlMode() == HtmlMode.Static) || (site.getHtmlMode() == HtmlMode.Ssi))
				site.setHomePath(Site.PATH_INDEX);

			if (webconfig.hasAttribute("NotFoundPath"))
				site.setNotFoundPath(new CommonPath(webconfig.getAttribute("NotFoundPath")));

			if (webconfig.getAttributeAsBooleanOrFalse("AlwaysCache"))
				site.setScriptCache(true);
			
			List<XElement> globals = sconfig.getTagListDeepFirst("Web.Global");
			boolean leagacyIcons = true;		// TODO switch to false, for now default to true
			
			if (webconfig.hasNotEmptyAttribute("LegacyIcons"))
				leagacyIcons = webconfig.getAttributeAsBooleanOrFalse("LegacyIcons");
			
			for (int i = globals.size() - 1; i >= 0; i--) {
				if (leagacyIcons) {
					if (globals.get(i).getAttributeAsBooleanOrFalse("NewIcon")) {
						globals.remove(i);
					}
				}
				else {
					if (globals.get(i).getAttributeAsBooleanOrFalse("LegacyIcon")) {
						globals.remove(i);
					}
				}
			}
			
			site.setWebGlobals(globals);
		}
		
		site.addDynamicAdapater("/css/dc.cache.css", new IWebWorkBuilder() {
			@Override
			public IOutputWork buildOutputAdapter(Site site, Path file, CommonPath loc, String view) throws OperatingContextException {
				IOutputWork work = new StyleCacheOutputAdapter();
				work.init(site, file, loc, view);
				return work;
			}

			@Override
			public IReviewWork buildReviewAdapter(Site site, Path file, CommonPath loc, String view) throws OperatingContextException {
				return null;  // does not apply
			}
		});
		
		site.addDynamicAdapater("/js/dc.cache.js", new IWebWorkBuilder() {
			@Override
			public IOutputWork buildOutputAdapter(Site site, Path file, CommonPath loc, String view) throws OperatingContextException {
				IOutputWork work = new ScriptCacheOutputAdapter();
				work.init(site, file, loc, view);
				return work;
			}

			@Override
			public IReviewWork buildReviewAdapter(Site site, Path file, CommonPath loc, String view) throws OperatingContextException {
				return null;  // does not apply
			}
		});

		site.addDynamicAdapater("/dcm/forms/loadwiz.js", new IWebWorkBuilder() {
			@Override
			public IOutputWork buildOutputAdapter(Site site, Path file, CommonPath loc, String view) throws OperatingContextException {
				IOutputWork work = new WizardOutputAdapter();
				work.init(site, file, loc, view);
				return work;
			}

			@Override
			public IReviewWork buildReviewAdapter(Site site, Path file, CommonPath loc, String view) throws OperatingContextException {
				return null;  // does not apply
			}
		});

		site.addDynamicAdapater("/dcw/manifest.json", new IWebWorkBuilder() {
			@Override
			public IOutputWork buildOutputAdapter(Site site, Path file, CommonPath loc, String view) throws OperatingContextException {
				IOutputWork work = new ManifestOutputAdapter();
				work.init(site, file, loc, view);
				return work;
			}

			@Override
			public IReviewWork buildReviewAdapter(Site site, Path file, CommonPath loc, String view) throws OperatingContextException {
				return null;  // does not apply
			}
		});

		for (XElement adaptor : sconfig.getTagListDeepFirst("Web.DynamicAdapter")) {
			String classname = adaptor.getAttribute("Class");
			String path = adaptor.getAttribute("Path");

			if (StringUtil.isEmpty(classname) || StringUtil.isEmpty(path))
				continue;

			IWebWorkBuilder workBuilder = null;

			try {
				workBuilder = (IWebWorkBuilder) site.getResources().getClassLoader().getInstance(classname);

				site.addDynamicAdapater(path, workBuilder);
			}
			catch (Exception x) {
				Logger.error("Bad Dynamic Adapater: " + x);
			}
		}

		// TODO enable the web cache Work
		//if (((this.htmlmode == HtmlMode.Dynamic) || (this.htmlmode == HtmlMode.Strict))
		//		&& (mod != null) && mod.isScriptStyleCached())
		//	this.buildScriptStyleCache();
	}

	/*
	// see Html class also - if scripts ever change
	public void buildScriptStyleCache(Site site) throws OperatingContextException {
		//System.out.println("Script Style Cache for: " + this.getSite().getTenant().getAlias() +
		//		"/" + this.getSite().getAlias());
		
		Task buildcache = Task.ofSubtask(
				"Script Style Cache for: " + site.getTenant().getAlias() +
						"/" + site.getAlias(),"WEB")
				.withTopic(WorkTopic.SYSTEM)
				.withWork(new IWork() {
					@Override
					public void run(TaskContext taskctx) throws OperatingContextException {
						Path sitepath = site.getPath();
						
						LocalStore fs = TenantHub.getFileStore();
						
						if (fs == null)
							return;
						
						// styles
						
						List<String> styles = site.webGlobalStyles(false, false);
						
						Memory bbw = new Memory();
						
						for (String style : styles) {
							WebFindResult wpath = site.webFindFilePath(CommonPath.from(style), null);
							
							if (wpath != null)
								bbw.write(IOUtil.readEntireFileToMemory(wpath.file).toArray());		// TODO min
							else
								Logger.error("Style not found: " + style);
						}
						
						Path stylepth = sitepath.resolve("www/css/dc-cache.min.css");
						
						IOUtil.saveEntireFile(stylepth, bbw);
						
						// scripts
						
						List<String> scripts = site.webGlobalScripts(false, false);
						
						bbw.setPosition(0);
						
						for (String script : scripts) {
							WebFindResult wpath = site.webFindFilePath(CommonPath.from(script), null);
							
							if (wpath != null)
								bbw.write(IOUtil.readEntireFileToMemory(wpath.file).toArray());		// TODO min
							else
								Logger.error("Script not found: " + script);
						}
						
						IOUtil.saveEntireFile(sitepath.resolve("www/js/dc-cache.min.js"), bbw);
						
						site.setScriptCache(true);
						
						taskctx.returnEmpty();
					}
				});
		
		TaskHub.submit(buildcache);
	}
	*/
	
	/* 
	 * 
			./tenants/[tenant alias]/config     
				- shared.xml - for tenant tier
				- schema.xml - for tenant tier
				- config.xml - for root site tier
				- dictionary.xml - for root site tier
				- vars.json - for root site tier
				
			./tenants/[tenant alias]/sites/[site alias]/config
				- config.xml - for site tier
				- dictionary.xml - for site tier
				- vars.json - for site tier
	 * 
	 */
	/*
	public void reloadSettings() {
		... "Service"

		boolean oldservices = true;
		
		Path spath = this.resolvePath("/services");

		if ((settings != null) && (settings.find("Services") != null)) {
			oldservices = false;
			
			/* TODO
			for (XElement pel :  settings.selectAll("Services/Service")) {
				String name = pel.getAttribute("Name");

				Path path = spath.resolve(name);
				
				TenantInfo.this.watchSettingsChange(path);
				
				this.registerService(new TenantServiceAdapter(name, path, TenantInfo.this.getPath(), pel)); 
			}
			 * /
		}
		
		// don't rely on this, deprecated - TODO remove someday
		if (oldservices && Files.exists(spath)) {
			this.watchSettingsChange(spath);
			
			try (Stream<Path> str = Files.list(spath)) {
				str.forEach(path -> {
					// only directories are services - files in dir are features
					if (! Files.isDirectory(path))
						return;
					
					Tenant.this.watchSettingsChange(path);
					
			        /* TODO
					String name = path.getFileName().toString();
					
					this.registerService(new TenantServiceAdapter(name, path, TenantInfo.this.getPath(), null));
					* /
				});
			} 
			catch (IOException x) {
				// TODO Auto-generated catch block
				x.printStackTrace();
			}
		}
		
		// discover CERTS
		
		/* TODO restore for dcraft.web
		WebTrustManager trustman = new WebTrustManager();
		trustman.init(settings);
		
		this.trustManagers[0] = trustman;
		* /
		
		// load SITES
		...
		
		// watcher comes after services so it can register a service if it likes... if this came before it would be cleared from the registered list
		this.watcher = new TenantWatcherAdapter(this.getPath());
		
		this.watcher.init(this);
		
		this.prepTenantSchedule();
		
		// TODO load directly - no events - ApplicationHub.fireEvent(HubEvents.TenantLoaded, this.getId());
	}
	
	public void prepTenantSchedule() {
		XElement settings = this.getSettings();
		
		if (settings != null) {
			// now load new schedules
			boolean prod = ApplicationHub.isProduction(); 
			
			Logger.info("Prepping schedules for " + this.getAlias());
			
			for (XElement schedule : settings.selectAll("Schedules/*")) {
				String sfor = schedule.getAttribute("For", "Production,Test");
				
				if (! prod && ! sfor.contains("Test"))
					continue;
				
				if (prod && ! sfor.contains("Production"))
					continue;
				
				Logger.info("- find schedule: " + schedule.getAttribute("Title"));
				
				ISchedule sched = "CommonSchedule".equals(schedule.getName()) ? new CommonSchedule() : new SimpleSchedule();
				
				sched.init(schedule);
				
				sched.setTask(Task
					.taskFromBuilder(new OperationContextBuilder().withRootTaskTemplate().withTenant(this.getAlias()))
					.withId(Task.nextTaskId("TenantSchedule"))
					.withTitle("Tenant Scheduled Task: " + schedule.getAttribute("Title"))
					.withWork(trun -> {
						Logger.info("Executing schedule: " + trun.getTask().getTitle() + " for domain " + OperationContext.getOrNull().getTenant().getAlias());
						
						if (schedule.hasAttribute("MethodName") && (this.watcher != null))
							this.watcher.tryExecuteMethod(schedule.getAttribute("MethodName"), new Object[] { trun });
					})
				);
			
				Logger.info("- prepped schedule: " + schedule.getAttribute("Title") + " next run " + new DateTime(sched.when()));
				
				this.schedulenodes.add(sched);
				
				ScheduleHub.addNode(sched);
			}
		}
	}

	// TODO site
	
	public void init(XElement settings) {
		...
		this.getTenant().watchSettingsChange(cpath);
		
		Path certpath = cpath.resolve("certs");
		
		if ((certpath != null) && Files.exists(certpath))
			this.getTenant().watchSettingsChange(certpath);

		Path bpath = this.resolvePath("/buckets");
		
		if ((bpath != null) && Files.exists(bpath)) 
			this.getTenant().watchSettingsChange(bpath);

		Path gpath = this.resolvePath("/glib");
		
		if ((gpath != null) && Files.exists(gpath)) 
			this.getTenant().watchSettingsChange(gpath);
		
		if (settings != null) {
			...
			
			/* TODO restore for dcraft.web
			Path certpath = this.resolvePath("config/certs");
	
			if (Files.exists(certpath)) {
				this.certs = new DomainNameMapping<>();
				
				for (XElement cel : settings.selectAll("Certificate")) {
					SslContextFactory ssl = new SslContextFactory();
					ssl.init(cel, certpath.toString() + "/", this.tenant.get().getTrustManagers());
					this.certs.add(cel.getAttribute("Name"), ssl);
				}
			}
			* /
		}
		
		/* TODO restore for dcraft.web
		this.website = WebSite.from((settings != null) ? settings.selectFirst("Web") : null, this);
		* /
	}
	*/
}
