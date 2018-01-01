package dcraft.tenant.work;

import dcraft.hub.op.OperatingContextException;
import dcraft.task.IWork;
import dcraft.task.TaskContext;
import dcraft.tenant.Tenant;

public class CleanupWork implements IWork {
	protected Tenant tenant = null;
	
	public void setTenant(Tenant v) {
		this.tenant = v;
	}
	
	@Override
	public void run(TaskContext trun) throws OperatingContextException {
		// =====================================
		// clear old settings
		// =====================================
		
		/* 
		// cancel and remove any previous watchers
		if (this.watchkeys.size() > 0) {
			Logger.info("Cancelling watchers for " + this.getAlias());
			
			for (WatchKey key : this.watchkeys) 
				ApplicationHub.unregisterFileWatcher(key);
		}
		
		// cancel and remove any previous schedules 
		if (this.schedulenodes.size() > 0) {
			Logger.info("Cancelling schedules for " + this.getAlias());
			
			for (ISchedule sch : this.schedulenodes) {
				Logger.info("- schedule: " + sch.task().getTitle());
				sch.cancel();
			}
		}
		
		// TODO stop watcher if currently operating

		for (Site site : this.sites.values())
			site.kill();
			*/
		
		trun.returnEmpty();
	}

	/*
	// sites
	public void kill() {
		for (Bucket b : this.buckets.values())
			b.tryExecuteMethod("Kill", this);
		
		this.buckets.clear();
	}
	*/
}
