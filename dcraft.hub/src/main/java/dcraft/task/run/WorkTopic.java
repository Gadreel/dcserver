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
package dcraft.task.run;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.TaskContext;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

/*
 * primary topics in dcraft - Default, Web, Batch, Bus
 */
public class WorkTopic {
	final static public String DEFAULT = "_Default";
	final static public String SYSTEM = "_System";
	
	static public WorkTopic of(String name, int runlimit) {
		return WorkTopic.of(name, (long)runlimit, null);
	}
	
	static public WorkTopic of(String name, Long runlimit, Long loadsize) {
		WorkTopic topic = new WorkTopic();
		topic.name = name;
		topic.runlimit = runlimit;
		topic.loadsize = loadsize;
		return topic;
	}
	
	static public WorkTopic of(XElement config, Long defaultloadsize) {
		// must have a name and must not start with _ for reserved topics
		if (config.hasEmptyAttribute("Name") || config.getAttribute("Name").startsWith("_")) {
			Logger.errorTr(1, "Missing or invalid work topic name");
			return null;
		}
		
		WorkTopic topic = new WorkTopic();
		
		if (config != null) {
			topic.runlimit = config.getAttributeAsInteger("RunLimit");
			topic.loadsize = config.getAttributeAsInteger("LoadSize");
			
			topic.name = config.getAttribute("Name");
			
			topic.automaticQueueLoader = config.getAttributeAsBooleanOrFalse("AutomaticQueueLoader");
		}
		
		if ((topic.runlimit != null) && (topic.runlimit < 1))
			topic.runlimit = null;
		
		if ((topic.runlimit == null) && (topic.loadsize == null))
			topic.loadsize = defaultloadsize;
		
		return topic;
	}
	
	protected String name = WorkTopic.DEFAULT;
	protected LinkedBlockingQueue<TaskContext> backlogqueue = new LinkedBlockingQueue<TaskContext>();
	protected HashMap<String, TaskContext> inprogress = new HashMap<>();
	protected boolean automaticQueueLoader = false;
	
	protected ReentrantLock topiclock = new ReentrantLock();
	
	// TODO don't like loadsize, even though it is evolved design
	protected Long runlimit = null;
	protected Long loadsize = null;
	protected boolean trace = false;
	
	protected WorkTopic() {
	}

	public String getName() {
		return this.name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public int getRunLimit() {
		return (this.runlimit == null) ? 0 : this.runlimit.intValue();
	}
	
	public void setRunLimit(int v) {
		this.runlimit = (long) v;
	}
	
	public boolean getAutomaticQueueLoader() {
		return this.automaticQueueLoader;
	}
	
	public void setAutomaticQueueLoader(boolean v) {
		this.automaticQueueLoader = v;
	}

	public int backlog() {
		return this.backlogqueue.size();		
	}

	public int inprogress() {
		return this.inprogress.size();		
	}
	
	public Collection<TaskContext> tasksInProgress() {
		return this.inprogress.values(); 
	}

	// if Full Size is in use we have a hard limit, otherwise a suggested limit of 150% thread count
	public int availCount() {
		if (this.runlimit != null)
			return this.runlimit.intValue() - this.inprogress.size();		
		
		return this.loadsize.intValue() - this.inprogress.size();
	}
	
	public boolean isFull() {
		if (this.runlimit == null) 
			return false;
		
		return (this.inprogress.size() >= this.runlimit.intValue());
	}

	public void setTrace(boolean v) {
		this.trace = v;
	}
	
	public RecordStruct toStatusReport() {
		RecordStruct rec = new RecordStruct();
		
		rec.with("Name", this.name);
		rec.with("InProgress", this.inprogress());
		rec.with("Backlogged", this.backlog());
		rec.with("RunLimit", this.getRunLimit());
		
		return rec;
	}

	// return true if pool can submit directly, otherwise return false and backlog it
	public boolean canSubmit(TaskContext run) {
		this.topiclock.lock();

		boolean isLost = true;
		
		try {
			// if this task is continuing (via resubmit) then it goes right on the queue
			if (this.inprogress.containsKey(run.getTask().getId())) {
				Logger.traceTr(199, run);
				return true;
			}

			// if our queue can take unlimited work then task goes right on the queue
			// this is fast, but could result in a large number of partially run tasks 
			// if any tasks are all async
			if (this.runlimit == null) {
				this.inprogress.put(run.getTask().getId(), run);		// adds only if not already in set - first time a task is taken that task is considered in progress until the task completes
				Logger.traceTr(199, run);
				return true;
			}
			
			// otherwise see if we have available space in the topic, backlog if not 
			int prog = this.inprogress.size();
			int avail = this.runlimit.intValue() - prog;
			
			if (avail > 0) {
				this.inprogress.put(run.getTask().getId(), run);		// adds only if not already in set - first time a task is taken that task is considered in progress until the task completes
				Logger.traceTr(199, run);
				
				isLost = false;
				
				prog++;
				avail--;
				
				if (this.trace) {
					System.out.println("------------------------------------------------");
					System.out.println("  Task Running: " + run.getTask().getId());
					System.out.println("------------------------------------------------");
					System.out.println("         Max: " + this.runlimit.intValue());
					System.out.println("     In Prog: " + prog);			// use var so it doesn't change between above and here
					System.out.println("       Avail: " + avail);
					System.out.println("  Backlogged: " + this.backlogqueue.size());
				}
				
				return true;
			}

			Logger.traceTr(211, run);
			this.backlogqueue.add(run);
			
			isLost = false;
			
			if (this.trace) {
				System.out.println("------------------------------------------------");
				System.out.println("  Task Backlogged: " + run.getTask().getId());
				System.out.println("------------------------------------------------");
				System.out.println("         Max: " + this.runlimit.intValue());
				System.out.println("     In Prog: " + prog);			// use var so it doesn't change between above and here
				System.out.println("       Avail: " + avail);
				System.out.println("  Backlogged: " + this.backlogqueue.size());
			}
		}
		catch(Exception x) {
			Logger.traceTr(212, run, x);		// TODO error?
			
			// if not in any queue then make sure we cleanup
			if (isLost)
				run.kill();		// TODO really should be no choice to continue
		}
		finally {
			this.topiclock.unlock();
		}
		
		return false;
	}

	public void took(TaskContext run) {
		if (this.trace) {
			System.out.println("------------------------------------------------");
			System.out.println("  Task Taken: " + run.getTask().getId());
			System.out.println("------------------------------------------------");
			System.out.println("     In Prog: " + this.inprogress.size());
			System.out.println("  Backlogged: " + this.backlogqueue.size());
		}
	}

	// complete a run.  return another run to queue if we are back logged
	public TaskContext complete(TaskContext run) {
		// if using backlog then see if there is room for a new task
		this.topiclock.lock();

		try {
			String completedid = run.getTask().getId();
			
			if (this.trace) {
				System.out.println(this.name + " Removing: " + completedid);
				
				for (TaskContext inrun : this.inprogress.values()) {
					String lid = inrun.getTask().getId();
					System.out.println("In list: " + lid + " - looks equal: " + lid.equals(completedid));
				}
			}
			
			if (this.trace) 
				System.out.println(this.name + " prog: " + this.inprogress.size());
			
			TaskContext rt = this.inprogress.get(completedid);
			
			if (this.trace) 
				System.out.println(this.name + " Got: " + rt);
			
			rt = this.inprogress.remove(completedid);
			
			if (this.trace) {
				System.out.println(this.name + " Removed: " + rt);
				
				for (TaskContext inrun : this.inprogress.values()) {
					String lid = inrun.getTask().getId();
					System.out.println("In list: " + lid + " - looks equal: " + lid.equals(completedid));
				}
			}
			
			// if no max size then no back log
			if (this.runlimit == null) 
				return null;
		
			int prog = this.inprogress.size();
			int avail = this.runlimit.intValue() - prog;		
			
			if (this.trace) {
				System.out.println("------------------------------------------------");
				System.out.println("  Task Completed: " + run.getTask().getId());
				System.out.println("------------------------------------------------");
				System.out.println("         Max: " + this.runlimit.intValue());
				System.out.println("     In Prog: " + prog);			// use var so it doesn't change between above and here
				System.out.println("       Avail: " + avail);
				System.out.println("  Backlogged: " + this.backlogqueue.size());
			}
			
			if ((avail > 0) && !this.backlogqueue.isEmpty()) {
				TaskContext r = this.backlogqueue.take();
				this.inprogress.put(r.getTask().getId(), r);
				return r;
			}
		}
		catch (InterruptedException x) {
			// shouldn't happen during normal run
		}
		finally {
			this.topiclock.unlock();
		}
		
		return null;
	}
	
	public void checkIfHung() {
		List<TaskContext> killlist = new ArrayList<>();
		
		this.topiclock.lock();
		
		try {
			for (TaskContext run : this.inprogress.values()) {
				if (run.isHung()) 
					killlist.add(run);
				else
					run.reviewClaim();
			}
		}
		finally {
			this.topiclock.unlock();
		}
		
		for (TaskContext run : killlist) {
			Logger.warn("Work Topic " + this.name + " found hung: " + run.getTask().getId());
			run.kill();
		}
	}
	
	public void stop() {
		List<TaskContext> killlist = new ArrayList<>();
		
		for (TaskContext run : this.inprogress.values()) 
			killlist.add(run);
		
		for (TaskContext run : this.backlogqueue) 
			killlist.add(run);
		
		for (TaskContext run : killlist) {
			Logger.warn("Work Topic " + this.name + " found hung: " + run.getTask().getId());
			run.kill();
		}
	}
	
	@Override
	public String toString() {
		return this.name;
	}

	public TaskContext findTask(String taskid) {
		return this.inprogress.get(taskid);		// TODO could try backlog too, let's assume this search is for active
	}
}
