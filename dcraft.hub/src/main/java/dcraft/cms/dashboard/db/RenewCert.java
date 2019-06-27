package dcraft.cms.dashboard.db;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.filevault.Vault;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.op.UserContext;
import dcraft.hub.resource.SslEntry;
import dcraft.hub.resource.TrustResource;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.IWork;
import dcraft.task.Task;
import dcraft.task.TaskContext;
import dcraft.task.TaskHub;
import dcraft.task.TaskObserver;
import dcraft.tenant.Site;
import dcraft.tenant.Tenant;
import dcraft.tenant.TenantHub;
import dcraft.tool.certs.CertUtil;
import dcraft.tool.certs.RenewSiteAutoWork;
import dcraft.tool.certs.RenewSiteManualWork;
import dcraft.util.KeyUtil;

import java.security.cert.X509Certificate;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

public class RenewCert implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();
		
		List<String> domains = data.getFieldAsList("Domains").toStringList();
		
		// TODO if production then do the AutoRenew work instead
		
		IWork work = ApplicationHub.isProduction()
				? RenewSiteAutoWork.of(data.getFieldAsString("Tenant"), data.getFieldAsString("Site"), domains)
				: RenewSiteManualWork.of(data.getFieldAsString("Tenant"), data.getFieldAsString("Site"), domains);
		
		TaskHub.submit(
				Task.ofSubtask("Renew Cert", "CERT")
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
