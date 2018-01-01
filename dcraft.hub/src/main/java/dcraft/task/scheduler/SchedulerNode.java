package dcraft.task.scheduler;

import dcraft.hub.op.*;
import dcraft.log.Logger;
import dcraft.task.ISchedule;
import dcraft.task.Task;
import dcraft.task.TaskContext;
import dcraft.task.TaskObserver;
import dcraft.task.scheduler.common.CommonSchedule;
import dcraft.util.StringUtil;

public class SchedulerNode implements IOperationObserver {
	protected SchedulerNode next = null;
	protected long when = 0;
	protected String scheduleid = null;
	
	// of the task being watched
	protected String taskopid = null;
	protected String taskid = null;
	
	public ISchedule getSchedule() {
		return ScheduleHub.schedules.get(this.scheduleid);
	}
	
	// only call in the main sched loop
	public boolean isCancelled() {
		ISchedule sched = this.getSchedule();
		
		if (sched == null)
			return true;
		
		if (sched.isCancelled()) {
			ScheduleHub.schedules.remove(this.scheduleid);		// done with the schedule
			return true;
		}
		
		return false;
	}
	
	@Override
	public void init(OperationContext target) {
		this.taskopid = target.getOpId();
		
		if (target instanceof TaskContext)
			this.taskid = ((TaskContext) target).getTask().getId();
	}
	
	@Override
	public ObserverState fireEvent(OperationContext target, OperationEvent event, Object detail) {
		// be sure we restore the context
		OperationContext ctx = OperationContext.getOrNull();
		
		try {
			if (event != OperationConstants.COMPLETED)
				return ObserverState.Continue;
			
			ISchedule sched = this.getSchedule();
			
			if (sched == null) {
				Logger.error("Missing BATCH scheduler: " + this.scheduleid);
				return ObserverState.Done;
			}
			
			if (StringUtil.isEmpty(this.taskopid) || StringUtil.isEmpty(this.taskid))
				return ObserverState.Done;
			
			// only works on the task we are interested in - events might fire for sub tasks or peer tasks, but ignore those
			if (! (target instanceof TaskContext))
				return ObserverState.Continue;
			
			TaskContext ttarget = (TaskContext) target;
			
			if (! this.taskopid.equals(ttarget.getOpId()) || ! this.taskid.equals(ttarget.getTask().getId()))
				return ObserverState.Continue;
			
			if (sched instanceof CommonSchedule)
				Logger.info("Complete code for BATCH scheduler: " + this.scheduleid);
			
			if (sched.reschedule())
				ScheduleHub.addNode(sched);
			else
				ScheduleHub.schedules.remove(this.scheduleid);		// done with the schedule
			
			return ObserverState.Done;
		}
		finally {
			OperationContext.set(ctx);
		}
	}
}
