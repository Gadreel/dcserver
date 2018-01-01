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
package dcraft.task;

import dcraft.hub.op.IOperationObserver;
import dcraft.hub.op.ObserverState;
import dcraft.hub.op.OperationConstants;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationEvent;
import dcraft.util.StringUtil;

abstract public class TaskObserver implements IOperationObserver {
	// of the task watching, if any
	protected OperationContext opcontext = null;
	
	// of the task being watched
	protected String taskopid = null;
	protected String taskid = null;
	
	public TaskObserver() {
		this.opcontext = OperationContext.getOrNull();
	}
	
	@Override
	public void init(OperationContext target) {
    	this.taskopid = target.getOpId();
    	
    	if (target instanceof TaskContext)
    		this.taskid = ((TaskContext) target).getTask().getId();
	}
	
    // fire is in the context that OO originated in, not in 
	@Override
	public ObserverState fireEvent(OperationContext target, OperationEvent event, Object detail) {
		// be sure we restore the context
		OperationContext ctx = OperationContext.getOrNull();
		
		try {
			if (event == OperationConstants.TOUCH) {
				if (this.opcontext != null)
					this.opcontext.touch();
				
				return ObserverState.Continue;
			}
			
			if (event != OperationConstants.COMPLETED)
				return ObserverState.Continue;
				
			if (StringUtil.isEmpty(this.taskopid) || StringUtil.isEmpty(this.taskid))
				return ObserverState.Continue;
			
			// only works on the task we are interested in - events might fire for sub tasks or peer tasks, but ignore those
			if (! (target instanceof TaskContext))
				return ObserverState.Continue;
			
			TaskContext ttarget = (TaskContext) target;
			
			if (! this.taskopid.equals(ttarget.getOpId()) || ! this.taskid.equals(ttarget.getTask().getId()))
				return ObserverState.Continue;
			
			// this will only get called once
			
			if (this.opcontext != null) {
				OperationContext.set(this.opcontext);
				this.opcontext = null;
			}
			
			this.callback(ttarget);
			
			return ObserverState.Done;
		}
		finally {
			OperationContext.set(ctx);
		}
	}
	
	abstract public void callback(TaskContext task);
}
