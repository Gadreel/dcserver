package dcraft.tool.backup;

import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.resource.ResourceTier;
import dcraft.hub.resource.SslEntry;
import dcraft.hub.resource.TrustResource;
import dcraft.log.Logger;
import dcraft.log.count.CountHub;
import dcraft.log.count.NumberCounter;
import dcraft.task.IWork;
import dcraft.task.TaskContext;
import dcraft.tenant.Site;
import dcraft.tenant.Tenant;
import dcraft.tenant.TenantHub;
import dcraft.util.FileUtil;
import dcraft.util.KeyUtil;

import java.math.BigDecimal;
import java.security.cert.X509Certificate;
import java.time.ZonedDateTime;
import java.util.Date;

public class CertCheckWork implements IWork {
	@Override
	public void run(TaskContext taskctx) throws OperatingContextException {
		StringBuilder report = new StringBuilder();
		
		ZonedDateTime checkdate = ZonedDateTime.now().plusDays(28);		// TODO switch to 10 once auto certificate system is in place
		
		ResourceTier sysources = ResourceHub.getTopResources();
		
		TrustResource trustResource = sysources.getTrust();
		
		if (trustResource != null) {
			for (SslEntry sslEntry : trustResource.getSslCerts()) {
				for (X509Certificate cert : sslEntry.getIssuedCerts()) {
					String subject = cert.getSubjectDN().toString();
					String thumbprint = KeyUtil.getCertThumbprint(cert);
					
					Date after = cert.getNotAfter();
					
					if (after.toInstant().isBefore(checkdate.toInstant())) {
						Logger.info("*****     System: "
								+ " - Subject: " + subject + " - Thumbprint: " + thumbprint
								+ " - Date: " + after);
						
						report.append("Shared Cert - Subject: " + subject + " - Thumbprint: " + thumbprint
								+ " - Date: " + after + "\n");
					}
					else {
						Logger.info("          System: "
								+ " - Subject: " + subject + " - Thumbprint: " + thumbprint
								+ " - Date: " + after);
					}
				}
			}
		}
		
		for (Tenant tenant : TenantHub.getTenants()) {
			for (Site site : tenant.getSites()) {
				ResourceTier siteresources = site.getTierResources();
				
				trustResource = siteresources.getTrust();
				
				if (trustResource != null) {
					for (SslEntry sslEntry : trustResource.getSslCerts()) {
						for (X509Certificate cert : sslEntry.getIssuedCerts()) {
							String subject = cert.getSubjectDN().toString();
							String thumbprint = KeyUtil.getCertThumbprint(cert);
							
							Date after = cert.getNotAfter();
							
							if (after.toInstant().isBefore(checkdate.toInstant())) {
								Logger.info("*****     Site: " + tenant.getAlias() + "/" + site.getAlias()
										+ " - Subject: " + subject + " - Thumbprint: " + thumbprint
										+ " - Date: " + after);
								
								report.append("Site Cert: " + tenant.getAlias() + "/" + site.getAlias()
										+ " - Subject: " + subject + " - Thumbprint: " + thumbprint
										+ " - Date: " + after + "\n");
							}
							else {
								Logger.info("          Site: " + tenant.getAlias() + "/" + site.getAlias()
										+ " - Subject: " + subject + " - Thumbprint: " + thumbprint
										+ " - Date: " + after);
							}
						}
					}
				}
			}
		}
		
		if (report.length() > 0) {
			BackupUtil.notifyProgress(ApplicationHub.getDeployment() + " : " + ApplicationHub.getNodeId() + " : Overdue Certificates\n" + report);
		}
		
		taskctx.returnEmpty();
	}
}
