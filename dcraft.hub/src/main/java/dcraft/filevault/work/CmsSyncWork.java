package dcraft.filevault.work;

import dcraft.filestore.FileStore;
import dcraft.filevault.DepositHub;
import dcraft.filevault.work.steps.CmsSyncDepositWork;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.script.inst.Sleep;
import dcraft.task.ChainWork;
import dcraft.task.ControlWork;
import dcraft.task.IWork;
import dcraft.task.TaskContext;
import dcraft.util.StringUtil;

public class CmsSyncWork extends ChainWork {
	static public CmsSyncWork of (String nodeid) {
		CmsSyncWork work = new CmsSyncWork();
		work.nodeid = nodeid;
		return work;
	}
	
	protected String nodeid = null;
	protected long depositid = 480;  // "000000000000000";  15
	protected FileStore depositstore = null;
	
	@Override
	protected void init(TaskContext taskctx) throws OperatingContextException {
		// get cloud file store
		this.depositstore = DepositHub.getCloudStore(null, null);
		
		if (this.depositstore == null) {
			Logger.error("Unable to access cloud file store");
			taskctx.returnEmpty();
			return;
		}
		
		thenSyncDeposit();
	}
	
	protected void thenSyncDeposit() {
		String did = StringUtil.leftPad(depositid + "", 15, '0');
		
		CmsSyncDepositWork syncDepositWork = CmsSyncDepositWork.of(nodeid, did, this.depositstore);
		
		this
				.then(syncDepositWork)
				.then(ControlWork.dieOnError("Unable to load deposit: " + did))
				.then(Sleep.tag(1).createStack(null))
				.then(new IWork() {
					@Override
					public void run(TaskContext taskctx) throws OperatingContextException {
						// TODO save number
						
						depositid++;
						
						// go to next
						CmsSyncWork.this.thenSyncDeposit();
						
						taskctx.complete();
					}
				});
	}
}
