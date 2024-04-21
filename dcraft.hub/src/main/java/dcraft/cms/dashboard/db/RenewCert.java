package dcraft.cms.dashboard.db;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.op.UserContext;
import dcraft.hub.resource.ResourceTier;
import dcraft.hub.resource.SslEntry;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.*;
import dcraft.tenant.Site;
import dcraft.tenant.TenantHub;
import dcraft.tool.certs.RenewSiteAutoWork;
import dcraft.tool.certs.RenewSiteManualWork;
import dcraft.xml.XElement;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

public class RenewCert implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();
		
		//List<String> domains = data.getFieldAsList("Domains").toStringList();
		
		String tenant = data.getFieldAsString("Tenant");
		String site = data.getFieldAsString("Site");
		Site renewsite = TenantHub.resolveTenant(tenant).resolveSite(site);

		// to renew
		List<String> domains = new ArrayList<>();

		ResourceTier resourceTier = renewsite.getTierResources();

		if (resourceTier != null) {
			for (XElement domain : resourceTier.getConfig().getTagListLocal("Domain")) {
				if (! Struct.objectToBoolean(domain.getAttribute("Certificate"), true))
					continue;

				domains.add(domain.getAttribute("Name"));
			}
		}

		System.out.println("Our Domains list " + domains);

		IWork work = ApplicationHub.isProduction()
				? RenewSiteAutoWork.of(domains)
				: RenewSiteManualWork.of(domains);

		TaskHub.submit(
				// run in the proper domain
				Task.of(OperationContext.context(UserContext.rootUser(tenant, site)))
						.withId(Task.nextTaskId("CERT"))
						.withTitle("Renew Cert")
						.withTimeout(5)
						.withWork(work),
				new TaskObserver() {
					@Override
					public void callback(TaskContext task) {
						callback.returnEmpty();
					}
				}
		);
	}
}
