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
package dcraft.task.scheduler;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.log.count.CountHub;
import dcraft.struct.RecordStruct;
import dcraft.task.ISchedule;
import dcraft.task.Task;
import dcraft.task.TaskContext;
import dcraft.task.TaskHub;
import dcraft.task.run.HungWorkSysWorker;
import dcraft.task.run.WorkHub;
import dcraft.task.run.WorkTopic;
import dcraft.task.scheduler.limit.LimitHelper;

/**
 * Handles scheduling application tasks.
 * 
 * @author Andy White
 *
 */
// TODO add tracing settings
public class ScheduleHub {
	// the first node in the list of scheduled nodes - the head of the list is moved
	// forward as the items on the list get scheduled.  List is single linked list.
	static protected SchedulerNode first = null;
	
	// key = schedule internal id 
	static protected ConcurrentHashMap<String, ISchedule> schedules = new ConcurrentHashMap<String,ISchedule>();

	// how many are currently in the linked list?
	static protected long nodeCnt = 0;
	
	// lock during adding and removing of scheduled work.  all add and remove operations
	// are thread safe
	static protected ReentrantLock lock = new ReentrantLock();
	
	static protected HashMap<String,LimitHelper> batches = new HashMap<String,LimitHelper>();
	
	//static protected ISchedulerDriver driver = null;
	
	static protected boolean active = false;
	static protected boolean sysworker = false;		// has system worker been setup yet - do so only once
	
	static public void minStart() {
		if (! ScheduleHub.sysworker) {
			ApplicationHub.getClock().addFastSystemWorker(new ScheduleExecuteSysWorker());
			ScheduleHub.sysworker = true;
		}
	}
	
	static public void minStop() {
	}
	
	static public LimitHelper getBatch(String name) {
		return ScheduleHub.batches.get(name);
	}
	
	static public void setActive(boolean v) {
		if (v)
			Logger.debugTr(0, "Scheduler Hub active");
		else
			Logger.debugTr(0, "Scheduler Hub inactive");
		
		ScheduleHub.active = v;
	}
	
	// see above
		/*
	static public void run() {
		if (ScheduleHub.driver != null) {
			ListStruct loadres = ScheduleHub.driver.loadSchedule();
			
			if (loadres != null) {				
				loadres.recordStream().forEach(rec -> {
					XElement schedule = rec.getFieldAsXml("Schedule");
					
					ISchedule sched = "CommonSchedule".equals(schedule.getName()) ? new CommonSchedule() : new SimpleSchedule();
					
					sched.init(schedule);
					
					sched.setTask(Task.taskWithHubRootContext()		// TODO need a way to collect the context from this - Site/Tenant
						.withId(Task.nextTaskId("ScheduleLoader"))
						.withTitle("Scheduled Task Loader: " + rec.getFieldAsString("Title"))
						.withWork(trun -> {
								ScheduleEntry loadres2 = ScheduleHub.driver.loadEntry(rec.getFieldAsString("Id"));
								
								if (loadres2 != null) {
									loadres2.setSchedule(sched);
									loadres2.submit();
								}
								
								// we are done, no need to wait 
								trun.complete();
						})
					);
					
					ScheduleHub.addNode(sched);
				});
			}
		}
	}
		*/

	static public long size() {
		return ScheduleHub.nodeCnt;
	}

	// the scheduler runs on its own thread, this is the code that starts and runs the scheduler
	// remember that sys workers should not use OperationContext
	static protected void execute() {
		if (! ScheduleHub.active) 
			return;
		
		long loadcnt = ScheduleHub.nodeCnt;
		
		ScheduleHub.lock.lock();

		try {
			SchedulerNode curr = ScheduleHub.first;

			long now = System.currentTimeMillis();
			
			//System.out.println(new DateTime() +  " - scheduler - " + new DateTime(now) + " > " + new DateTime(curr.when));

			while ((curr != null) && (curr.when <= now)) {
				//System.out.println("Scheduled node: " + curr.scheduler.isCanceled());
				
				// we are assuming and trusting that no Operation Context should be set or present
				if (! curr.isCancelled()) {
					ISchedule schedule = curr.getSchedule();
					
					if (schedule == null) {
						Logger.warn("Schedule BATCH missing for node: " + curr.scheduleid);
					}
					else {
						Task task = schedule.getTask();
						
						if ((task == null) || (task.getContext() == null)) {
							Logger.warn("Schedule BATCH missing task or context: " + schedule.getTitle());
						}
						else {
							RecordStruct params = task.getCreateHints();
							
							params.with("_ScheduleId", schedule.getCreateId());
							params.with("_ScheduleHints", schedule.getHints());
							
							TaskHub.submit(task, curr);
						}
					}
				}
				
				SchedulerNode old = curr;
				
				curr = old.next;
				ScheduleHub.first = curr;
				ScheduleHub.nodeCnt--;
				
				// reduce references for better GC
				old.next = null;
			}
			
			loadcnt = ScheduleHub.nodeCnt;
		}
		catch(Exception x) {
			Logger.error("Error running scheduler: " + x);
		}		
		finally {
			ScheduleHub.lock.unlock();
		}
		
		// it is possible due to race conditions to get a mis-ordered value in the counter
		// a) it doesn't matter, b) we cannot afford to do this in the lock
		CountHub.allocateSetNumberCounter("dcSchedulerLoad", loadcnt);
	}
	
	// do not reuse a context when scheduling, give it a fresh one or a subcontext
	static public ISchedule addNode(ISchedule schedule) {
		long when = schedule.when();
		
		if (when < 0)
			return null;
		
		ScheduleHub.schedules.put(schedule.getCreateId(), schedule);
		
    	SchedulerNode snode = new SchedulerNode();
    	
    	snode.when = when;
    	snode.scheduleid = schedule.getCreateId();
		
		long loadcnt = ScheduleHub.nodeCnt;
		
		ScheduleHub.lock.lock();

        try {
			SchedulerNode curr = ScheduleHub.first;
            SchedulerNode last = null;

			ScheduleHub.nodeCnt++;

			// loop through the scheduling linked list and find the right place to insert
			// the new TScheduleNode
            while (curr != null) {
				if (snode.when < curr.when) {
                    snode.next = curr;

					if (last == null) 
						ScheduleHub.first = snode;
					else 
						last.next = snode;

                    return schedule;
                }

                last = curr;
				curr = curr.next;
            }

            // none found then add to end
			if (last == null) 
				ScheduleHub.first = snode;
			else 
				last.next = snode;
			
			loadcnt = ScheduleHub.nodeCnt;
        }
        catch(Exception x) {
        	// TODO

            return null;
        }
        finally {
        	ScheduleHub.lock.unlock();
        }
		
		// it is possible, due to race conditions, to get a mis-ordered value in the counter
		// a) it doesn't matter 99.99999999% of the time, b) we cannot afford to do this in the lock
		CountHub.allocateSetNumberCounter("dcSchedulerLoad", loadcnt);

        return schedule;
	}
	
	static public void dump() {
		/*
		SchedulerNode curr = Scheduler.first;
		int cnt = 0;

        while (curr != null) {
    		//Logger.info("     + " + curr.task.getTitle());
        	
        	System.out.println("   " + TimeUtil.stampFmt.print(curr.when) + " - [" + curr.scheduler.getClass().getName() + "] " + curr.task.getTitle() + " (" + curr.task.getContext().getOpId() + ") @ " + curr.task.getDebugWorkname());
        	
        	cnt++;
			curr = curr.next;
        }
        
        System.out.println("Outstanding schedule nodes: " + cnt);
        */
	}
}
