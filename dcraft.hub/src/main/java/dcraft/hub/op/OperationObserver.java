/* ************************************************************************
#
#  designCraft.io
#
#  http://designcraft.io/
#
#  Copyright:
#    Copyright 2014 eTimeline, LLC. All rights reserved.
#
#  License:
#    See the license.txt file in the project's top-level directory for details.
#
#  Authors:
#    * Andy White
#
************************************************************************ */
package dcraft.hub.op;

import dcraft.struct.RecordStruct;
import dcraft.task.TaskContext;

// just toss out the events, useful only for subclassing
// this is durable for use with Queue but remember only fields are saved in database
// and you should implement deepCopy
abstract public class OperationObserver extends RecordStruct implements IOperationObserver {
	@Override
	public void init(OperationContext target) {
    	this.with("OperationId", target.getOpId());
    	
    	if (target instanceof TaskContext)
    		this.with("TaskId", ((TaskContext) target).getTask().getId());
	}
	
    // fire is in the context that OO originated in, not in 
	@Override
	public ObserverState fireEvent(OperationContext target, OperationEvent event, Object detail) {
		// be sure we restore the context
		OperationContext ctx = OperationContext.getOrNull();
		
		try {
			if (event == OperationConstants.LOG)
				this.log(target, (RecordStruct) detail);
			else if (event == OperationConstants.PROGRESS) {
				if (target instanceof TaskContext) {
					TaskContext ttarget = (TaskContext) target;
					
					if (detail == OperationConstants.PROGRESS_AMOUNT)
						this.amount(target, ttarget.getAmountCompleted());
					else if (detail == OperationConstants.PROGRESS_STEP)
						this.step(target, ttarget.getCurrentStep(), ttarget.getSteps(), ttarget.getCurrentStepName());
					else
						this.progress(target, ttarget.getProgressMessage());
				}
			}
			else if (event == OperationConstants.COMPLETED)
				this.completed(target);
			else if (event == OperationConstants.PREP_TASK)
				this.prep(target);
			else if (event == OperationConstants.START_TASK)
				this.start(target);
			else if (event == OperationConstants.STOP_TASK)
				this.stop(target);
		}
		finally {
			OperationContext.set(ctx);
		}
		
		return ObserverState.Continue;
	}
	
	public void log(OperationContext ctx, RecordStruct entry) {
	}
	
	public void step(OperationContext ctx, int num, int of, String name){
	}
	
	public void progress(OperationContext ctx, String msg){
	}
	
	public void amount(OperationContext ctx, int v){
	}

	public void completed(OperationContext ctx) {
	}
	
	public void prep(OperationContext ctx) {
	}
	
	public void start(OperationContext ctx) {
	}
	
	public void stop(OperationContext ctx) {
	}
}
