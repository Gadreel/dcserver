package dcraft.cms.dashboard.db;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.resource.ConfigResource;
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
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.security.cert.X509Certificate;
import java.time.ZonedDateTime;

public class LoadMissingCerts implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		ListStruct resp = ListStruct.list();
		
		for (Tenant tenant : TenantHub.getTenants()) {
			for (Site site : tenant.getSites()) {
				ListStruct opendomains = ListStruct.list();
				ListStruct blockeddomains = ListStruct.list();
				
				ConfigResource sconfig = site.getResources().getConfig();
				TrustResource tr = site.getResources().getTrust();
				
				for (XElement del : sconfig.getTagListLocal("Domain")) {
					String dname = del.getAttribute("Name");
					
					if (StringUtil.isEmpty(dname))
						continue;

					if (tr.matchSsl(dname) == null) {
						if (del.getAttributeAsBooleanOrFalse("Certificate"))
							opendomains.with(dname);
						else
							blockeddomains.with(dname);
					}
				}

				if ((opendomains.size() > 0) || (blockeddomains.size() > 0)) {
					resp.with(
							RecordStruct.record()
									.with("Tenant", tenant.getAlias())
									.with("Site", site.getAlias())
									.with("AutoDomains", opendomains)
									.with("ManualDomains", blockeddomains)
					);
				}
			}
		}
		
		callback.returnValue(resp);
	}
}
