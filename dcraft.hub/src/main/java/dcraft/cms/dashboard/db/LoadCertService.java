package dcraft.cms.dashboard.db;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.resource.SslEntry;
import dcraft.hub.resource.TrustResource;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.tenant.Site;
import dcraft.tenant.Tenant;
import dcraft.tenant.TenantHub;
import dcraft.tool.certs.CertUtil;
import dcraft.util.KeyUtil;
import org.shredzone.acme4j.Session;

import java.security.cert.X509Certificate;
import java.time.ZonedDateTime;

public class LoadCertService implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		ListStruct resp = ListStruct.list();
		
		try {
			Session session = CertUtil.newSession(true);
			
			// Only LE for now
			resp.with(
					RecordStruct.record()
							.with("Title", "Let's Encrypt")
							.with("Alias", "LE")
							.with("Terms", session.getMetadata().getTermsOfService())
							.with("Enabled", CertUtil.checkAccount(session, CertUtil.loadUserKeyPair()) != null)
			);
			
			callback.returnValue(resp);
		}
		catch (Exception x) {
			Logger.error("Problem loading services");
			callback.returnEmpty();
		}
	}
}
