package dcraft.filevault.work;

import dcraft.db.BasicRequestContext;
import dcraft.db.DatabaseAdapter;
import dcraft.db.DatabaseException;
import dcraft.db.IConnectionManager;
import dcraft.db.fileindex.BasicFilter;
import dcraft.db.fileindex.FileIndexAdapter;
import dcraft.db.proc.ExpressionResult;
import dcraft.db.util.ByteUtil;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStoreFile;
import dcraft.filevault.Vault;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.UserContext;
import dcraft.hub.resource.ConfigResource;
import dcraft.hub.resource.ResourceTier;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.IWork;
import dcraft.task.StateWork;
import dcraft.task.StateWorkStep;
import dcraft.task.Task;
import dcraft.task.TaskContext;
import dcraft.task.TaskHub;
import dcraft.task.TaskObserver;
import dcraft.tenant.Site;
import dcraft.tenant.Tenant;
import dcraft.tenant.TenantHub;
import dcraft.xml.XElement;
import dcraft.xml.XmlReader;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class IndexAllFilesWork extends StateWork {
	protected Deque<Site> sites = new ArrayDeque<>();
	
	protected StateWorkStep indexSite = null;
	
	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this
				.withStep(StateWorkStep.of("Prep Sites List", this::prepSites))
				.withStep(indexSite = StateWorkStep.of("Index Site", this::doSite));
	}
	
	public StateWorkStep prepSites(TaskContext trun) throws OperatingContextException {
		if (! ResourceHub.getResources().getDatabases().hasDefaultDatabase()) {
			Logger.error("No local database, cannot index vault files");
			return StateWorkStep.STOP;
		}
		
		for (Tenant tenant : TenantHub.getTenants())
			for (Site site : tenant.getSites())
				this.sites.addLast(site);
		
		return this.indexSite;
	}
	
	public StateWorkStep doSite(TaskContext trun) throws OperatingContextException {
		Site site = this.sites.pollFirst();
		
		if (site == null)
			return StateWorkStep.STOP;
		
		OperationContext tctx = OperationContext.context(
				UserContext.rootUser(site.getTenant().getAlias(), site.getAlias()));
		
		TaskHub.submit(
				Task.ofContext(tctx)
						.withTitle("File Index a site")
						.withTimeout(10)
						.withWork(new IndexSiteFilesWork()),
				new TaskObserver() {
					@Override
					public void callback(TaskContext task) {
						// causes the `doSite` step to run again
						trun.resume();
					}
				}
		);
		
		return StateWorkStep.WAIT;
	}
}
