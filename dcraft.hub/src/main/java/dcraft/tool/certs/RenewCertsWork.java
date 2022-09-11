package dcraft.tool.certs;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.UserContext;
import dcraft.hub.resource.ResourceTier;
import dcraft.hub.resource.SslEntry;
import dcraft.log.Logger;
import dcraft.struct.Struct;
import dcraft.task.*;
import dcraft.tenant.Site;
import dcraft.tenant.Tenant;
import dcraft.tenant.TenantHub;
import dcraft.tool.backup.BackupUtil;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class RenewCertsWork extends StateWork {
	protected StateWorkStep findsites = null;
	protected StateWorkStep processsites = null;
	
	protected Deque<Site> sites = new ArrayDeque<>();
	protected ZonedDateTime checkdate = null;

	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this.withSteps(
				findsites = StateWorkStep.of("Find stale certs", this::findSites),
				processsites = StateWorkStep.of("Process a site", this::proccessSite)
		);

		this.checkdate = ZonedDateTime.now().plusDays(13);		// TODO configure
	}
	
	public StateWorkStep findSites(TaskContext trun) throws OperatingContextException {
		for (Tenant tenant : TenantHub.getTenants()) {
			for (Site site : tenant.getSites()) {
				if (CertUtil.getPastDueSiteCerts(site, this.checkdate).size() > 0) {
					this.sites.addLast(site);
				}
			}
		}

		return StateWorkStep.NEXT;
	}
	
	public StateWorkStep proccessSite(TaskContext trun) throws OperatingContextException {
		trun.touch();

		Site site = this.sites.pollFirst();

		if (site == null)
			return StateWorkStep.STOP;

		Path siteconfig = site.resolvePath("config");

		Path certspath = siteconfig.resolve("certs.key");

		if (Files.notExists(certspath)) {
			Logger.info("No key file, skipping SSL cert renewal for " + site.getTenant().getAlias() + " / " + site.getAlias());
			return StateWorkStep.REPEAT;
		}

		// to renew
		List<String> domains = new ArrayList<>();
		List<String> finaldomains = new ArrayList<>();

		ResourceTier resourceTier = site.getTierResources();

		if (resourceTier != null) {
			for (XElement domain : resourceTier.getConfig().getTagListLocal("Domain")) {
				if (! Struct.objectToBoolean(domain.getAttribute("Certificate"), true))
					continue;

				domains.add(domain.getAttribute("Name"));
			}
		}

		for (String domain : domains) {
			SslEntry entry = resourceTier.getTrust().matchTierSsl(domain);

			if (entry != null) {
				for (X509Certificate cert : entry.getIssuedCerts()) {
					if (cert.getNotAfter().toInstant().isBefore(this.checkdate.toInstant())) {
						finaldomains.add(domain);
					}
				}
			}
		}

		if (finaldomains.size() == 0) {
			Logger.info("Past due but not auto-renew, skipping SSL cert renewal for " + site.getTenant().getAlias() + " / " + site.getAlias());
			return StateWorkStep.REPEAT;
		}

		trun.touch();

		Logger.info("Attempting to renew SSL cert for: " + StringUtil.join(finaldomains, ", "));

		BackupUtil.notifyProgress(ApplicationHub.getDeployment() + " : " + ApplicationHub.getNodeId() + " : Auto Renew Cert: " + site.getTenant().getAlias() + " " + site.getAlias() + " : " + StringUtil.join(finaldomains, ", "));

		TaskHub.submit(
				// run in the proper domain
				Task.of(OperationContext.context(UserContext.rootUser(site.getTenant().getAlias(), site.getAlias())))
						.withId(Task.nextTaskId("CERT"))
						.withTitle("Renew Cert")
						.withTimeout(5)
						.withWork(ApplicationHub.isProduction() ? RenewSiteAutoWork.of(finaldomains) : RenewSiteManualWork.of(finaldomains)),
				new TaskObserver() {
					@Override
					public void callback(TaskContext task) {
						RenewCertsWork.this.transition(trun, StateWorkStep.REPEAT);
					}
				}
		);
		
		return StateWorkStep.WAIT;
	}
}
