package dcraft.core.db.config;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.resource.ConfigResource;
import dcraft.hub.resource.SslEntry;
import dcraft.hub.resource.TrustResource;
import dcraft.locale.LocaleDefinition;
import dcraft.locale.LocaleUtil;
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

public class LoadTenantDomains implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		ListStruct resp = ListStruct.list();
		
		for (Site site : OperationContext.getOrThrow().getTenant().getSites()) {
			ConfigResource sconfig = site.getResources().getConfig();

			for (XElement lel : sconfig.getTagListLocal("Locale")) {
				String lname = LocaleUtil.normalizeCode(lel.getAttribute("Name"));

				if (StringUtil.isEmpty(lname))
					continue;

				for (XElement del : lel.selectAll("Domain")) {
					RecordStruct dresp = expandDomain(del);

					if (dresp != null)
						resp.with(dresp);
				}
			}

			for (XElement del : sconfig.getTagListLocal("Domain")) {
				RecordStruct dresp = expandDomain(del);

				if (dresp != null)
					resp.with(dresp);
			}
		}
		
		callback.returnValue(resp);
	}

	public RecordStruct expandDomain(XElement del) {
		if (del == null)
			return null;

		String dname = del.getAttribute("Name");

		if (StringUtil.isEmpty(dname))
			return null;

		return RecordStruct.record()
				.with("Domain", dname)
				.with("Certificate", del.getAttributeAsBooleanOrFalse("Certificate"))
				.with("Use", del.getAttribute("Use"));
	}
}
