package dcraft.filevault.work.steps;

import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStore;
import dcraft.filestore.local.LocalStore;
import dcraft.filestore.local.LocalStoreFile;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.stream.StreamWork;
import dcraft.task.ChainWork;
import dcraft.task.IWork;
import dcraft.task.TaskContext;

public class DownloadManifestWork extends ChainWork {
	static public DownloadManifestWork of(String nodeid, String depositId, FileStore store) {
		DownloadManifestWork work = new DownloadManifestWork();
		work.nodeid = nodeid;
		work.depositid = depositId;
		work.remote = store;
		return work;
	}
	
	protected String nodeid = null;
	protected String depositid = null;
	protected FileStore remote = null;
	protected LocalStoreFile local = null;
	
	public LocalStoreFile getLocal() {
		return this.local;
	}
	
	@Override
	protected void init(TaskContext taskctx) throws OperatingContextException {
		LocalStore nodeDepositStore = LocalStore.of(ApplicationHub.getDeploymentPath().resolve("nodes/" + this.nodeid + "/deposits"));
		
		this.local = nodeDepositStore.resolvePathToStore("/chain/" + this.depositid + ".chain");
		
		if (this.local.exists()) {
			this.then(new IWork() {
				@Override
				public void run(TaskContext taskctx) throws OperatingContextException {
					Logger.info("Chain file already present for: " + DownloadManifestWork.this.depositid);
					taskctx.returnEmpty();
				}
			});
		}
		else {
			this.then(StreamWork.of(
				this.remote.fileReference(CommonPath.from("/deposits/" + DownloadManifestWork.this.nodeid
						+ "/chain/" + DownloadManifestWork.this.depositid + ".chain"))
						.allocStreamSrc(),
				
				DownloadManifestWork.this.local.allocStreamDest()
			));
		}
	}
}
