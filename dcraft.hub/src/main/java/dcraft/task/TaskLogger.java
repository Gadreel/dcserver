package dcraft.task;

import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationLogger;
import dcraft.struct.RecordStruct;

public class TaskLogger extends OperationLogger {
	static public TaskLogger of(String taskid) {
		TaskLogger logger = new TaskLogger();
		logger.taskid = taskid;
		return logger;
	}
	
	// of the task being watched
	protected String taskid = null;
	
	@Override
	public void init(OperationContext target) {
    	if (target instanceof TaskContext)
    		this.taskid = ((TaskContext) target).getTask().getId();
	}
	
	@Override
	public void log(OperationContext ctx, RecordStruct entry) {
		// do nothing if we aren't in a task - this is a Task Logger 
		if (this.taskid == null)
			return;
		
		// we only care about logging to our task or subtasks
    	if (! (ctx instanceof TaskContext))
    		return;
    	
    	// the current task id
    	String cid = ((TaskContext) ctx).getTask().getId();
    	
    	// if cid is = taskid then this is logging to our task
    	// if cid starts with task then this is logging to a subtask of our task
    	// either way do the logging
    	if (cid.startsWith(this.taskid))
    		super.log(ctx, entry);
	}
}
