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
package dcraft.util.cb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dcraft.hub.op.IOperationObserver;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationObserver;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.log.Logger;
import dcraft.task.IWork;
import dcraft.task.Task;
import dcraft.task.TaskContext;
import dcraft.task.TaskHub;

public class TaskCountDownCallback extends CountDownCallback {
	protected int delayComplete = 2;
	protected List<Task> tasks = new ArrayList<>();
	protected HashMap<String, TaskContext> runs = new HashMap<>();
	
	protected IOperationObserver taskCallback = new OperationObserver() {
		@Override
		public void completed(OperationContext or) {
			TaskCountDownCallback.this.countDown();
		}				
	};
	
	public void setDelayComplete(int v) {
		this.delayComplete = v;
	}
	
	public IOperationObserver getTaskCallback() {
		return this.taskCallback;
	}
	
	public TaskCountDownCallback(OperationOutcomeEmpty callback) {
		super(0, callback);
	}
	
	public List<Task> getTasks() {
		return this.tasks;
	}
	
	public Map<String, TaskContext> getRuns() {
		return this.runs;
	}
	
	public TaskContext getRun(String id) {
		return this.runs.get(id);
	}

	@Override
	public int countDown() {
		this.cdlock.lock();
		
		try {
			int res = this.count.decrementAndGet();
			
			if (res < 0)
				res = 0;
			
			// make this a delayed action
			if (res == 0) {
				// be sure we are running as the task that requested this callback
				Task reporttask = Task.of(this.callback.getOperationContext())
					.withWork(new IWork() {
						@Override
						public void run(TaskContext run) {
							TaskCountDownCallback.this.callback.returnResult();
							run.returnEmpty();
						}
					});
				
				TaskHub.scheduleIn(reporttask, this.delayComplete);		
			}
			
			return res;
		} 
		catch (OperatingContextException x) {
			Logger.error("Countdown was called in the wrong context: " + x);
			return -1;
		}
		finally {
			this.cdlock.unlock();
		}
	}

	public TaskCountDownCallback with(Task... tasks) {
		this.increment();
		
		for (Task task : tasks)
			this.tasks.add(task);
		
		return this;
	}
	
	public void submit() {
		for (Task t : this.tasks) {
			TaskContext run = TaskHub.submit(t, this.getTaskCallback());
			
			this.runs.put(t.getId(), run);
		}
	}
}
