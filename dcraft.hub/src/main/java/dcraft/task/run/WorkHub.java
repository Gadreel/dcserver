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
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.IOperationObserver;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.log.count.CountHub;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.*;

public class WorkHub {
	static protected LinkedBlockingQueue<TaskContext> queue = new LinkedBlockingQueue<>();
	static protected Worker[] slots = new Worker[0];
	static protected ConcurrentHashMap<String, WorkTopic> topics = new ConcurrentHashMap<>();
	
	// when set, the pool will work only on N number of tasks until one of those tasks completes
	// where upon a new task from the general queue "queue" can be accepted
	// in other words, when fullsize == inprogress.size() we are full and do no additional processing
	// when this is null, we pull tasks off the general queue ANY time a thread has spare cycles
	
	static protected AtomicLong totalThreadsCreated = new AtomicLong();
	static protected AtomicLong totalThreadsHung = new AtomicLong();		// based on timeout
	
	static protected boolean shutdown = false;		// TODO replace with state - starting, running, stopping, stopped
	static protected boolean sysworker = false;		// has system worker been setup yet - do so only once
	
	static public void minStart() {
		// do nothing if already started
		if (WorkHub.slots.length > 0)
			return;
		
		int size = 16;
		
		WorkHub.topics.clear();
		
		// place the default topic in - it might be overridden in config
		WorkHub.addTopic(WorkTopic.of(WorkTopic.DEFAULT, null, (long) size));
		
		// the system topic is always exactly 1 to prevent more than one at a time
		WorkHub.addTopic(WorkTopic.of(WorkTopic.SYSTEM, 1L, 1L));
		
		CountHub.allocateSetNumberCounter("dcWorkPool_Topics", WorkHub.topics.size());
		
		CountHub.allocateSetNumberCounter("dcWorkPool_Threads", size);
		
		WorkHub.resizeSlots(size);
		
		if (! WorkHub.sysworker) {
			ApplicationHub.getClock().addSlowSystemWorker(new HungWorkSysWorker());
			WorkHub.sysworker = true;
		}
	}

	static public void addTopic(WorkTopic topic) {
		WorkHub.topics.put(topic.getName(), topic);
	}

	static public void removeTopic(String name) {
		WorkHub.topics.remove(name);
	}
	
	static public int threadCount() {
		return WorkHub.slots.length; 
	}
	
	static public long threadsCreated() {
		return WorkHub.totalThreadsCreated.get();
	}
	
	static public void incThreadsCreated() {
		long n = WorkHub.totalThreadsCreated.incrementAndGet();
		
		CountHub.allocateSetNumberCounter("dcWorkPool_ThreadsCreated", n);
	}
	
	static public long threadsHung() {
		return WorkHub.totalThreadsHung.get();
	}
	
	static public void incThreadsHung() {
		long n = WorkHub.totalThreadsHung.incrementAndGet();
		
		CountHub.allocateSetNumberCounter("dcWorkPool_ThreadsHung", n);
	}
	
	static public Collection<WorkTopic> getTopics() {
		return WorkHub.topics.values();
	}
	
	static public boolean hasTopic(String v) {
		return WorkHub.topics.containsKey(v);
	}
	
	// submit should be able to run without having a OperationContext
	
	// this might accept "resubmits" or "new" - either way we should run "complete" if it fails
	static public void submit(TaskContext run, IOperationObserver... observers) {
		if (run == null)
			return;
		
		// warn if new task during shut down
		if (WorkHub.shutdown && ! run.hasStarted())
			Logger.warnTr(197, run);
		
		// make sure context and logging, etc are ready
		// this will also catch if run was resubmitted but killed
		if (! run.prep(observers) && ! run.hasStarted()) {
			Logger.errorTr(216, run);		// TODO different error messages if resume
			run.complete();		// TODO needs testing, kill?
			return;
		}
		
		if (run.isComplete())
			return;
		
		// if resume then see if we are a currently running thread, if so just reuse the thread (throttling allowing)
		if (run.hasStarted()) {
			Worker[] workers = WorkHub.slots;
			
			if (run.getSlot() < workers.length) {
				Worker w = workers[run.getSlot()];
				
				if ((w != null) && w.resume(run))
					return;
			}
		}
		
		// find the work topic
		WorkTopic topic = WorkHub.getTopicOrDefault(run);
		
		// see if the topic advises a submit, if not the topic will hold onto the run
		// in a wait queue.  if true then we put right on the active work queue
		if (topic.canSubmit(run)) 
			WorkHub.queue.add(run);
	}
	
	static public TaskContext take() throws InterruptedException {
		TaskContext run = WorkHub.queue.take();
		
		// find the work topic
		WorkTopic topic = WorkHub.getTopicOrDefault(run);
		
		// let the topic know this run is in progress
		topic.took(run);
		
		return run;
	}

	static public void complete(TaskContext run) {
		// find the work topic
		WorkTopic topic = WorkHub.getTopicOrDefault(run);
		
		// tell the topic to complete run
		TaskContext newrun = topic.complete(run);
		
		// see if the topic advises a submit
		if (newrun != null) {
			Logger.traceTr(199, newrun);
			WorkHub.queue.add(newrun);
		}
	}
	
	static public WorkTopic getTopicOrDefault(String name) {
		WorkTopic topic = WorkHub.topics.get(name);
		
		if (topic != null)
			return topic;
		
		return WorkHub.topics.get(WorkTopic.DEFAULT);
	}
	
	static public WorkTopic getTopicOrDefault(TaskContext run) {
		WorkTopic topic = WorkHub.topics.get(run.getTask().getTopic());
		
		if (topic != null)
			return topic;
		
		return WorkHub.topics.get(WorkTopic.DEFAULT);
	}
	
	static protected List<Worker> resizeSlots(int size) {
		Worker[] oldslots = WorkHub.slots;
		
		Worker[] newslots =  new Worker[size];
		
		// reuse existing slots as much as possible
		for (int i = 0; (i < size) && (i < oldslots.length); i++) {
			newslots[i] = oldslots[i];
		}
		
		WorkHub.slots = newslots;
		
		// initialize new slots (if size if > before)
		for (int i = 0; i < newslots.length; i++) {
			if (newslots[i] == null)
				WorkHub.initSlot(i);
		}
		
		List<Worker> oldworkers = new ArrayList<>();
		
		// phase out slots that are no longer needed (if size < before)
		for (int i = size; i < oldslots.length; i++) {
			oldslots[i].retire();
			oldworkers.add(oldslots[i]);
		}
		
		return oldworkers;
	}
	
	static protected void initSlot(int slot) {
		if (! WorkHub.shutdown) {
			Worker work = new Worker();
			WorkHub.slots[slot] = work;
			work.start(slot);
		}
		
		//Logger.trace("Thread Pool slot " + slot + " changed, now have " + WorkPool.slots.size() + " threads");
	}

	static public int queued() {
		return WorkHub.queue.size();		
	}
	
	static public void minStop() {
		WorkHub.shutdown = true;
		
		// quickly let everyone know it is time to stop
		Logger.traceTr(0, "Work Pool Stopping Nice");
		
		for (int i = 0; i < WorkHub.slots.length; i++) {
			Worker w = WorkHub.slots[i];
			
			if (w != null)
				w.stopNice();
		}
		
		Logger.traceTr(0, "Work Pool Waiting");
		
		int remaincnt = 0;
		
		// wait a minute for things to finish up.   -- TODO config
		for (int i2 = 0; i2 < 60; i2++) {
			remaincnt = 0;
			
			for (int i = 0; i < WorkHub.slots.length; i++) {
				Worker w = WorkHub.slots[i];
				
				if ((w != null) && w.isBusy())
					remaincnt++;
			}
			
			if (remaincnt == 0)
				break;
			
			try {
				Thread.sleep(1000);
			}
			catch (Exception x) {
			}
		}
		
		Logger.traceTr(0, "Work Pool Size: " + remaincnt);
		
		Logger.traceTr(0, "Work Pool Interrupt Remaining Workers");
		
		for (int i = 0; i < WorkHub.slots.length; i++) {
			Worker w = WorkHub.slots[i];
			
			if (w != null)
				w.stop();
		}
		
		Logger.traceTr(0, "Work Pool Cleaning Topics");
		
		for (WorkTopic topic : WorkHub.topics.values())
			topic.stop();
		
		Logger.traceTr(0, "Work Pool Stopped");
	}
	
	static public RecordStruct toStatusReport() {
		RecordStruct rec = new RecordStruct();
		
		rec.with("Queued", WorkHub.queued());
		rec.with("Threads", WorkHub.threadCount());
		rec.with("ThreadsCreated", WorkHub.threadsCreated());
		rec.with("ThreadsHung", WorkHub.threadsHung());
		
		ListStruct topics = new ListStruct();
		
		for (WorkTopic topic : WorkHub.topics.values()) 
			topics.withItem(topic.toStatusReport());

		rec.with("Topics", topics);
		
		return rec;
	}
	
	// for a task by identity alone
	static public RecordStruct status(String taskid) {
		/* TODO */
		
		return null;
	}
	
	// for a task by identity plus workid (slightly more secure)
	/*
	static public RecordStruct status(String taskid, String workid) {
		for (WorkTopic topic : WorkPool.topics.values()) {
			TaskRun run = topic.findTask(taskid);
			
			if (run != null) 
				return run.status();
		}

		return null;
	}
	*/
	
	static public TaskContext findTask(String taskid) {
		for (WorkTopic topic : WorkHub.topics.values()) {
			TaskContext run = topic.findTask(taskid);
			
			if (run != null) 
				return run;
		}

		// TODO could search queue too, but let's try without that
		
		return null;
	}
	
	static public int inprogress() {
		int cnt = 0;
		
		for (WorkTopic topic : WorkHub.topics.values()) 
			cnt += topic.inprogress();
		
		return cnt;
	}
}
