package dcraft.cms.dashboard.db;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.op.UserContext;
import dcraft.struct.RecordStruct;
import dcraft.task.IWork;
import dcraft.task.Task;
import dcraft.task.TaskContext;
import dcraft.task.TaskHub;
import dcraft.task.TaskObserver;
import dcraft.tool.certs.RenewSiteAutoWork;
import dcraft.tool.certs.RenewSiteManualWork;

import java.util.List;

public class RenewCert implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();
		
		List<String> domains = data.getFieldAsList("Domains").toStringList();
		
		String tenant = data.getFieldAsString("Tenant");
		String site = data.getFieldAsString("Site");

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
