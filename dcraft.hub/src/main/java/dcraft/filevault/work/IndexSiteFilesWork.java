package dcraft.filevault.work;

import dcraft.hub.op.OperatingContextException;
import dcraft.task.StateWorkStep;
import dcraft.task.TaskContext;

public class IndexSiteFilesWork extends IndexFilesCoreWork {
	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this.sites.add(trun.getSite());
		
		this
				.withStep(indexSite = StateWorkStep.of("Index Site", this::doSite))
				.withStep(indexVault = StateWorkStep.of("Index Vault", this::doVault))
				.withStep(indexFolder = StateWorkStep.of("Index Folder", this::doFolder))
				.withStep(done = StateWorkStep.of("Done", this::done));
	}
}
