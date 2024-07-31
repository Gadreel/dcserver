package dcraft.core.work.admin;

import dcraft.core.util.AdminUtil;
import dcraft.custom.tool.cms.FeedAutomationWork;
import dcraft.filestore.CommonPath;
import dcraft.filevault.IndexTransaction;
import dcraft.filevault.TransactionFile;
import dcraft.filevault.Vault;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.UserContext;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.*;
import dcraft.tenant.Site;
import dcraft.tenant.Tenant;
import dcraft.tenant.TenantHub;
import dcraft.util.TimeUtil;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class FileUpdatesWork extends StateWork {
	// list of paths relative to the `tenants` folder of a deployment
	static public FileUpdatesWork of(ListStruct updatepaths, ListStruct deletepaths) {
		FileUpdatesWork work = new FileUpdatesWork();

		if (updatepaths != null) {
			for (int i = 0; i < updatepaths.size(); i++) {
				CommonPath tenantpath = CommonPath.from(updatepaths.getItemAsString(i));

				if (tenantpath != null)
					work.updatepaths.add(tenantpath);
			}
		}

		if (deletepaths != null) {
			for (int i = 0; i < deletepaths.size(); i++) {
				CommonPath tenantpath = CommonPath.from(deletepaths.getItemAsString(i));

				if (tenantpath != null)
					work.deletapaths.add(tenantpath);
			}
		}

		return work;
	}

	protected StateWorkStep doInit = null;
	protected StateWorkStep selectVaults = null;
	protected StateWorkStep transaction = null;
	protected StateWorkStep finish = null;

	protected ZonedDateTime now = TimeUtil.now();

	protected int filemode = 0;  // 0 = delete, 1 = update, 2 = done
	protected List<CommonPath> updatepaths = new ArrayList<>();
	protected List<CommonPath> deletapaths = new ArrayList<>();

	protected AdminUtil.VaultPlanOrganizer organizer = new AdminUtil.VaultPlanOrganizer();

	protected Deque<AdminUtil.VaultUpdatePlan> plans = new LinkedList<>();

	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this
				.withStep(this.doInit = StateWorkStep.of("Init", this::doInit))
				.withStep(this.selectVaults = StateWorkStep.of("Collect Queue Message", this::doTenantPlan))
				.withStep(this.transaction = StateWorkStep.of("Extract Payload Message", this::doVaultTransaction))
				.withStep(this.finish = StateWorkStep.of("Finish", this::doFinish));
	}

	public StateWorkStep doInit(TaskContext trun) throws OperatingContextException {
		Logger.debug("Start processing file updates for tenants");

		if ((this.updatepaths.size() == 0) && (this.deletapaths.size() == 0))
			return this.finish;

		return StateWorkStep.NEXT;
	}

	public StateWorkStep doTenantPlan(TaskContext trun) throws OperatingContextException {
		if (this.filemode > 1)
			return StateWorkStep.NEXT;

		List<CommonPath> paths = (this.filemode == 0) ? this.deletapaths : this.updatepaths;

		for (int n = 0; n < paths.size(); n++) {
			CommonPath path = paths.get(n);

			if (path.getNameCount() > 1) {
				String tenant = path.getName(0);
				String site = "root";
				CommonPath sitepath = path.subpath(1);

				if ("sites".equals(path.getName(1)) && (path.getNameCount() > 2)) {
					site = path.getName(2);
					sitepath = path.subpath(3);
				}

				Tenant t = TenantHub.resolveTenant(tenant);

				if (t != null) {
					Site s = t.resolveSite(site);

					if (s != null) {
						AdminUtil.SiteVaultPath siteVaultPath = AdminUtil.SiteVaultPath(t, s, sitepath);

						if (siteVaultPath != null) {
							if (this.filemode == 0)
								this.organizer.delete(t, s, siteVaultPath);
							else
								this.organizer.update(t, s, siteVaultPath);
						}
					}
					else {
						Logger.warn("Unable to find tenant site: " + path);
					}
				}
				else {
					Logger.warn("Unable to find tenant: " + path);
				}
			}
			else {
				Logger.trace("Skipping path: " + path);
			}
		}

		if (this.filemode == 0) {
			this.filemode++;
			return StateWorkStep.REPEAT;
		}

		this.organizer.collect(this.plans);

		return StateWorkStep.NEXT;
	}

	public StateWorkStep doVaultTransaction(TaskContext trun) throws OperatingContextException {
		AdminUtil.VaultUpdatePlan plan = this.plans.pollFirst();

		if (plan == null)
			return StateWorkStep.NEXT;

		IndexTransaction tx = IndexTransaction.of(plan.vault);

		Logger.info("Updating vault for: " + plan.tenant.getAlias() + " - " + plan.site.getAlias() + " - " + plan.vault.getName());

		for (int i = 0; i < plan.updates.size(); i++) {
			CommonPath path = plan.updates.get(i);
			TransactionFile file = TransactionFile.of(path, tx.getTimestamp());
			tx.withUpdate(file);
		}

		for (int i = 0; i < plan.deletes.size(); i++) {
			CommonPath path = plan.deletes.get(i);
			TransactionFile file = TransactionFile.of(path, tx.getTimestamp());
			tx.withDelete(file);
		}

		TaskHub.submit(
				// run in the proper domain
				Task.of(OperationContext.context(UserContext.rootUser(plan.tenant.getAlias(), plan.site.getAlias())))
						.withId(Task.nextTaskId("CMS"))
						.withTitle("Index Feed")
						.withTimeout(5)
						.withWork(new IWork() {
							@Override
							public void run(TaskContext taskctx) throws OperatingContextException {
								tx.commit();

								taskctx.returnEmpty();
							}
						}),
				new TaskObserver() {
					@Override
					public void callback(TaskContext task) {
						FileUpdatesWork.this.transition(trun, StateWorkStep.REPEAT);
					}
				}
		);

		return StateWorkStep.WAIT;
	}

	public StateWorkStep doFinish(TaskContext trun) throws OperatingContextException {
		Logger.debug("Finish processing file updates for tenants");

		return StateWorkStep.STOP;
	}
}
