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
package dcraft.stream;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationObserver;
import dcraft.log.Logger;
import dcraft.task.IWork;
import dcraft.task.TaskContext;
import dcraft.task.TaskObserver;

import java.util.Collection;

public class StreamWork extends StreamFragment implements IWork {
	static public StreamWork of(IStream... steps) {
		StreamWork work = new StreamWork();
		work.withAppend(steps);
		return work;
	}
	
	static public StreamWork of(StreamFragment... frags) {
		StreamWork work = new StreamWork();
		
		for (StreamFragment frag : frags)
			work.withAppend(frag);
		
		return work;
	}
	
	protected boolean init = false;
	
	protected StreamWork() {
	}
	
	public StreamWork with(StreamFragment fragment) {
		super.withAppend(fragment);
		return this;
	}
	
	public StreamWork with(Collection<IStream> steps) {
		super.withAppend(steps);
		return this;
	}
	
	public StreamWork with(IStream... steps) {
		super.withAppend(steps);
		return this;
	}

	@Override
	public void run(TaskContext trun) throws OperatingContextException {
		if (! this.init) {
			if (this.steps.size() < 2) {
				trun.kill("Stream steps must contain at least a source and destination step");
				return;
			}
			
			if (! (this.steps.get(0) instanceof IStreamSource)) {
				trun.kill("Stream steps must contain a source as the first step");
				return;
			}
			
			if (! (this.steps.get(steps.size() - 1) instanceof IStreamDest<?>)) {
				trun.kill("Stream steps must contain a destination as the last step");
				return;
			}
			
			IStream last = null;
			
			// skip any null stream in the middle
			for (int i = 0; i < steps.size(); i++) {
				IStream curr = steps.get(i);
				
				if (curr == null)
					continue;
				
				if (last != null)
					curr.setUpstream((IStreamUp) last);
				
				last = curr;
			}
			
			// if a stream finishes normally then it will cleanup itself, but when it does
			// not, ask it to release the resources it allocated - use task observer so we
			// respond to cleanup only when the current task is complete regardless of the
			// other tasks in this OperationController
			trun.getController().addObserver(new TaskObserver() {
				@Override
				public void callback(TaskContext task) {
					if (StreamWork.this.steps.size() == 0)
						return;
					
					IStream d = StreamWork.this.steps.get(StreamWork.this.steps.size() - 1);
					
					if (d != null)
						try {
							d.cleanup();
						} 
						catch (OperatingContextException x) {
							Logger.warn("Problem cleaning up Stream Work: " + x);
						}
					
					StreamWork.this.steps.clear();
				}
			});
			
			this.init = true;
		}
		
		// since streams are Pull based we tell the destination to start
		IStreamDest<?> d = (IStreamDest<?>) StreamWork.this.steps.get(StreamWork.this.steps.size() - 1);
		
		if (d != null)
			d.execute();
		else 
			trun.kill("Attempted to run StreamWork but missing dest.");
	}
}
