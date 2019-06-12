package dcraft.tool.certs;

import dcraft.hub.op.OperatingContextException;
import dcraft.task.StateWork;
import dcraft.task.StateWorkStep;
import dcraft.task.TaskContext;
import dcraft.tenant.Site;
import dcraft.tenant.Tenant;
import dcraft.tenant.TenantHub;

import java.util.List;

public class AutoRenewSiteWork extends StateWork {
	static public AutoRenewSiteWork of(String tenant, String site, List<String> domains) {
		AutoRenewSiteWork work = new AutoRenewSiteWork();

		work.tenant = tenant;
		work.site = site;
		work.domains = domains;

		return work;
	}

	protected StateWorkStep init = null;

	protected String tenant = null;
	protected String site = null;
	protected List<String> domains = null;

	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this.withSteps(
				init = StateWorkStep.of("Init SSL renewal", this::init)
		);
	}

	public StateWorkStep init(TaskContext trun) throws OperatingContextException {

		// TODO

		return StateWorkStep.NEXT;
	}
}
