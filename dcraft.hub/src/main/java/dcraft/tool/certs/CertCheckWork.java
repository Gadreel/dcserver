package dcraft.tool.certs;

import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.resource.ResourceTier;
import dcraft.hub.resource.SslEntry;
import dcraft.hub.resource.TrustResource;
import dcraft.log.Logger;
import dcraft.log.count.CountHub;
import dcraft.log.count.NumberCounter;
import dcraft.struct.RecordStruct;
import dcraft.task.IWork;
import dcraft.task.TaskContext;
import dcraft.tenant.Site;
import dcraft.tenant.Tenant;
import dcraft.tenant.TenantHub;
import dcraft.tool.backup.BackupUtil;
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

		// TODO switch to 10 once auto certificate system is in place
		int days = 15;

		RecordStruct params = taskctx.getTask().getParamsAsRecord();

		if (params != null)
			days = (int) params.getFieldAsInteger("CheckDays", days);

		ZonedDateTime now = ZonedDateTime.now();
		ZonedDateTime checkdate = now.plusDays(days);

		int pastdue = 0;
		int soon = 0;

		for (X509Certificate cert : CertUtil.getPastDueTopCerts(checkdate)) {
			Date after = cert.getNotAfter();

			soon++;

			if (after.toInstant().isBefore(now.toInstant()))
				pastdue++;

			String subject = cert.getSubjectDN().toString();
			String thumbprint = KeyUtil.getCertThumbprint(cert);

			Logger.info("*****     System: "
					+ " - Subject: " + subject + " - Thumbprint: " + thumbprint
					+ " - Date: " + after);

			report.append("Shared Cert - Subject: " + subject + " - Thumbprint: " + thumbprint
					+ " - Date: " + after + "\n");
		}
		
		for (Tenant tenant : TenantHub.getTenants()) {
			for (Site site : tenant.getSites()) {
				for (X509Certificate cert : CertUtil.getPastDueSiteCerts(site, checkdate)) {
					Date after = cert.getNotAfter();

					soon++;

					if (after.toInstant().isBefore(now.toInstant()))
						pastdue++;

					String subject = cert.getSubjectDN().toString();
					String thumbprint = KeyUtil.getCertThumbprint(cert);

					Logger.info("*****     Site: " + tenant.getAlias() + "/" + site.getAlias()
							+ " - Subject: " + subject + " - Thumbprint: " + thumbprint
							+ " - Date: " + after);

					report.append("Site Cert: " + tenant.getAlias() + "/" + site.getAlias()
							+ " - Subject: " + subject + " - Thumbprint: " + thumbprint
							+ " - Date: " + after + "\n");
				}
			}
		}

		// TODO move this out a level, so that nightly batch does this and this can be more general
		if (report.length() > 0) {
			BackupUtil.notifyProgress(ApplicationHub.getDeployment() + " : " + ApplicationHub.getNodeId() + " : Overdue Certificates\n" + report);
		}
		
		taskctx.returnValue(RecordStruct.record()
				.with("Pastdue", pastdue)
				.with("Soon", soon)
		);
	}
}
