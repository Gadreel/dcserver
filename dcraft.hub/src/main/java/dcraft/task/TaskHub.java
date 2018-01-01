package dcraft.task;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAccessor;
import java.util.Collection;

import dcraft.task.queue.QueueHub;
import org.threeten.extra.PeriodDuration;

import dcraft.hub.op.IOperationObserver;
import dcraft.hub.op.OperatingContextException;
import dcraft.task.run.WorkHub;
import dcraft.task.run.WorkExecutorService;
import dcraft.task.scheduler.ScheduleHub;
import dcraft.task.scheduler.SimpleSchedule;

public class TaskHub {
	static protected WorkExecutorService executor = new WorkExecutorService();
	
	static public void minStart() {
		WorkHub.minStart();
		ScheduleHub.minStart();
	}
	
	static public void minStop() {
		ScheduleHub.minStop();
		WorkHub.minStop();
	}
	
	static public WorkExecutorService getExecutorService() {
		return TaskHub.executor;
	}
	
	static public void execute(Runnable command) throws OperatingContextException {
		if (command instanceof TaskContext)
			WorkHub.submit((TaskContext)command);		// useful for resume
		else {
			Task builder = Task.ofSubContext()
				.withWork(command);
			
			TaskHub.submit(builder);
		}
	}
	
	static public TaskContext submit(IWork work) throws OperatingContextException {
		Task task = Task.ofSubContext()
			.withWork(work);
	
		return TaskHub.submit(task);
	}
	
	static public TaskContext submit(Task task) {
		TaskContext run = new TaskContext(task);
		
		WorkHub.submit(run);
		
		return run;
	}
	
	static public TaskContext submit(IWork work, Collection<IOperationObserver> observers) throws OperatingContextException {
		Task task = Task.ofSubContext()
			.withWork(work);
		
		return TaskHub.submit(task, observers);
	}
	
	static public TaskContext submit(IWork work, IOperationObserver... observers) throws OperatingContextException {
		Task task = Task.ofSubContext()
			.withWork(work);
	
		return TaskHub.submit(task, observers);
	}
	
	static public TaskContext submit(Task task, IOperationObserver... observers) {
		TaskContext run = new TaskContext(task);
		
		WorkHub.submit(run, observers);
		
		return run;
	}
	
	static public TaskContext submit(Task task, Collection<IOperationObserver> observers) {
		TaskContext run = new TaskContext(task);
		
		TaskHub.submit(run, observers);
		
		return run;
	}
	
	static public void submit(TaskContext run, Collection<IOperationObserver> observers) {
		WorkHub.submit(run, observers.toArray(new IOperationObserver[0]));
	}
	
	static public void submit(TaskContext run, IOperationObserver... observers) {
		WorkHub.submit(run, observers);
	}
	
	// add a work unit to run (almost) immediately - much the same as directly adding to the thread pool
	// any work unit submitted to the scheduler (or to any thread pool) will become owned by the
	// scheduler (or thread pool).
	static public ISchedule scheduleNow(Task task) {
    	return ScheduleHub.addNode(SimpleSchedule.of(task.freezeToRecord(), task.getWorkIfPresent(), System.currentTimeMillis(), 0));
    }

	// run the work unit once in Sec seconds from now
	static public ISchedule scheduleIn(Task task, int secs) {
		return ScheduleHub.addNode(SimpleSchedule.of(task.freezeToRecord(), task.getWorkIfPresent(), System.currentTimeMillis() + (1000 * secs), 0));
	}

	// run the work unit once at the specified time.  If less than now then submits immediately
	// if in the distant future, it may not be run if the process is terminated.  adding working
	// to the schedule is no guarantee work will be run.
	static public ISchedule scheduleAt(Task task, TemporalAccessor time) {
		return ScheduleHub.addNode(SimpleSchedule.of(task.freezeToRecord(), task.getWorkIfPresent(), Instant.from(time).toEpochMilli(), 0));
	}
	
	static public ISchedule scheduleAt(Task task, LocalDate date, PeriodDuration period) {
		LocalDateTime ldt = LocalDateTime.of(date, LocalTime.of(0, 0).plus(period));
		return TaskHub.scheduleAt(task, ldt);
	}

	// run the work unit repeatedly, every Secs seconds - note scheduler will not own work, you have to keep track of it
	static public ISchedule scheduleEvery(Task task, int secs) {
		return ScheduleHub.addNode(SimpleSchedule.of(task.freezeToRecord(), task.getWorkIfPresent(), System.currentTimeMillis() + (1000 * secs), secs));
	}
	
	static public void queueLocalTask(Task info) {
		QueueHub.queueLocalTask(info);
	}
	
	static public void queueGlobalTask(Task info) {
		QueueHub.queueGlobalTask(info);
	}
}
