package dcraft.cms.dashboard.db;

import dcraft.cms.thread.db.ThreadUtil;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.proc.filter.CurrentRecord;
import dcraft.db.proc.filter.Max;
import dcraft.db.proc.filter.Unique;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.resource.ResourceTier;
import dcraft.hub.resource.SslEntry;
import dcraft.hub.resource.TrustResource;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.tenant.Site;
import dcraft.tenant.Tenant;
import dcraft.tenant.TenantHub;
import dcraft.tool.certs.CertUtil;
import dcraft.util.KeyUtil;

import java.security.cert.X509Certificate;
import java.time.ZonedDateTime;
import java.util.Date;

public class LoadCerts implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		ListStruct resp = ListStruct.list();
		
		ZonedDateTime checkdate = ZonedDateTime.now().plusDays(28);		// TODO revise, configure
		
		TrustResource trustResource = ResourceHub.getTopResources().getTrust();
		
		if (trustResource != null) {
			for (SslEntry sslEntry : trustResource.getTierSslCerts()) {
				for (X509Certificate cert : sslEntry.getIssuedCerts()) {
					resp.with(
							RecordStruct.record()
									.with("Flagged", cert.getNotAfter().toInstant().isBefore(checkdate.toInstant()))
									.with("Tenant", "[shared]")
									.with("Site", "[shared]")
									.with("Thumbprint", KeyUtil.getCertThumbprint(cert))
									.with("Subject", cert.getSubjectDN().toString())
									.with("Domains", ListStruct.list(CertUtil.getNames(cert)))
									.with("Issuer", cert.getIssuerDN().getName())
									.with("Issued", Struct.objectToDateTime(cert.getNotBefore().toInstant()))
									.with("Expiration", Struct.objectToDateTime(cert.getNotAfter().toInstant()))
					);
				}
			}
		}
		
		for (Tenant tenant : TenantHub.getTenants()) {
			for (Site site : tenant.getSites()) {
				trustResource = site.getTierResources().getTrust();
				
				if (trustResource != null) {
					for (SslEntry sslEntry : trustResource.getTierSslCerts()) {
						for (X509Certificate cert : sslEntry.getIssuedCerts()) {
							resp.with(
									RecordStruct.record()
											.with("Flagged", cert.getNotAfter().toInstant().isBefore(checkdate.toInstant()))
											.with("Tenant", tenant.getAlias())
											.with("Site", site.getAlias())
											.with("Thumbprint", KeyUtil.getCertThumbprint(cert))
											.with("Subject", cert.getSubjectDN().toString())
											.with("Domains", ListStruct.list(CertUtil.getNames(cert)))
											.with("Issuer", cert.getIssuerDN().getName())
											.with("Issued", Struct.objectToDateTime(cert.getNotBefore().toInstant()))
											.with("Expiration", Struct.objectToDateTime(cert.getNotAfter().toInstant()))
							);
						}
					}
				}
			}
		}
		
		callback.returnValue(resp);
	}
}
