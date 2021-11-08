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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.IOperationObserver;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationConstants;
import dcraft.hub.op.OperationContext;
import dcraft.log.DebugLevel;
import dcraft.log.HubLog;
import dcraft.log.Logger;
import dcraft.session.Session;
import dcraft.struct.BaseStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.run.WorkHub;
import dcraft.util.FileUtil;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;

/**
 * Do not run same task object in parallel 
 * 
 */
public class TaskContext extends OperationContext implements Runnable {
	protected Task task = null;		// TODO move to field
	protected boolean isFromWorkQueue = false;
	protected int currentTry = 1;
	//protected long exitcode = 0L;
	//protected String exitmessage = null;
	
	// progress tracking - TODO switch to progress stack, save in field
    protected int progTotalSteps = 0;
    protected int progCurrStep = 0;
    protected String progStepName = null;
    protected int progComplete = 0;	
    protected String progMessage = null;

    // state and operations
	protected long started = -1;  
	protected int slot = 0;
	protected AtomicBoolean completedflag = new AtomicBoolean();
	protected long lastclaimed = -1; 
	
	protected boolean called = false;
	protected ISchedule timeout = null;		// TODO use this idea for timeout, deadline and reclaim
	//protected ReentrantLock oplock = new ReentrantLock();
	
	protected Deque<IWork> workchain = new ArrayDeque<>();
	
	public boolean hasStarted() {
		return (this.started > -1);
	}
	
	// don't alter this after submitting to work pool, this is for view only as submit
	public Task getTask() {
		return this.task;
	}
	
	public long getExitCode() {
		return this.getFieldAsInteger("ExitCode", 0);
	}
	
	public String getExitMessage() {
		return this.getFieldAsString("ExitMessage");
	}
	
	public void setSlot(int v) {
		this.slot = v;
	}
	
	public int getSlot() {
		return this.slot;
	}

	public void setIsFromWorkQueue(boolean v) {
		this.isFromWorkQueue = v;
	}
	
	public boolean isFromWorkQueue() {
		return this.isFromWorkQueue;
	}
	
	public void setCurrentTry(int v) {
		this.currentTry = v;
	}
	
	public int getCurrentTry() {
		return this.currentTry;
	}

	protected TaskContext() { }

	public TaskContext(Task info) {
		this.copyFields(info.getContext());
		this.task = info;
		this.with("ExitCode", 0);
		
		this.init();
	}

	// prep is running in external context, log to that context but 
	public boolean prep(IOperationObserver... observers) {
		// if we are resuming, leave the rest alone
		if (this.started != -1)
			return false;		// not prepared again, 

		OperationContext curr = OperationContext.getOrNull();

		try {
			OperationContext.set(this);

			this.task.prep();

			this.workchain.addFirst(this.task.buildWork());

			this.with("Params", this.task.getParams());

			this.task.cleanUp();        // no longer rely on Task for User, Controller or Work

			// add built in observers
			ListStruct buildobservers = this.task.getObservers();

			if ((buildobservers != null) && (buildobservers.size() > 0)) {
				for (BaseStruct s : buildobservers.items()) {
					RecordStruct orec = (RecordStruct) s;

					if (orec.isFieldEmpty("_Classname")) {
						Logger.warn("Missing observer classname (" + this.task.getId() + "): " + orec);
						continue;
					}

					IOperationObserver observer = (IOperationObserver) ResourceHub.getResources().getClassLoader().getInstance(orec.getFieldAsString("_Classname").toString());

					// copy any parameters for the observer
					if (observer instanceof RecordStruct)
						((RecordStruct) observer).copyFields(orec);

					observer.init(this);    // observer may wish to pickup the task id

					this.getController().addObserver(observer);
				}
			}

			// add any new observers
			if (observers.length > 0) {
				for (IOperationObserver observer : observers) {
					observer.init(this);    // observer may wish to pickup the task id

					this.getController().addObserver(observer);
				}
			}

			Session sess = this.getSession();

			if (sess != null)
				sess.registerTask(this);        // might use this for task tracking or remote debugging

			this.getController().fireEvent(this, OperationConstants.PREP_TASK, null);
		}
		finally {
			OperationContext.set(curr);
		}

		return true;
	}
	
	public boolean isComplete() {
		return this.completedflag.get();
	}

	// must report if timed out, even if completed - otherwise Worker thread might lock forever if WorkTopic kills us first
	public boolean isHung() {
		return this.isInactive() || this.isOverdue();
	}
	
	public boolean isInactive() {	
		long timeout = this.task.getTimeoutMS();
        
        //System.out.println("Get last activity in active test: " + this.getLastActivity());
		
		// has activity been quiet for longer than timeout?  
		if ((timeout > 0) && (this.getLastActivity() < (System.currentTimeMillis() - timeout))) 
				return true;
		
		return false;
	}
	
	// only become overdue after it has started
	public boolean isOverdue() {	
		long deadline = this.task.getDeadlineMS();
		
		// has activity been working too long?
		if ((this.started != -1) && (deadline > 0) && (this.started < (System.currentTimeMillis() - deadline)))
				return true;
		
		return false;
	}
	
	public BaseStruct getParams() {
		return this.getField("Params");
	}
	
	public void setParams(BaseStruct v) {
		this.with("Params", v);
	}
	
	// if task has been doing work but not fast enough we may need to renew/review claim
	// will not work if you use less than 2 minutes for timeout
	public void reviewClaim() {
		// if not started, if completed or if hung then nothing to review
		if ((this.started == -1) || this.completedflag.get() || this.isHung())
			return;

		// once every 5 seconds we can renew a claim  (might cause problems if run log is huge and we are tracking work back to the database)
		if (this.lastclaimed >= (System.currentTimeMillis() - 5000))
			return;
		
		// otherwise there has been activity recently enough to warrant and update
		this.updateClaim();
	}
	
	// return true if claimed or completed - false if canceled or timed out
	public boolean updateClaim() {
		/* TODO restore for Work Queue feature
		if (this.task.isFromWorkQueue()) { 
			// an incomplete load from work queue - edge error condition
			if (!this.task.hasAuditId()) {
				Logger.errorTr(191, this.task.getId());
				return false;
			}
			
			// get the logs up to date as much as possible
			OperationResult res1 = WorkQueue.trackWork(this, false);		// TODO add param for update claim?  review this
			
			if (res1.hasErrors()) {
				Logger.errorTr(191, this.task.getId());
				return false;
			}
			
			// try to extend our claim
			OperationResult res2 = WorkQueue.updateClaim(this.task);
			
			if (res2.hasErrors()) {
				Logger.errorTr(191, this.task.getId());
				return false;
			}
		}
		*/
		
		this.lastclaimed = System.currentTimeMillis();		
		
		return true;
	}
	public void engageDebugger() {
		Logger.debug("Debugger requested");

        	/* cleaning up ---
		this.debugmode = true;

		if (this.inDebugger)
			return;

		// need a task run to do debugging
		if (this.opcontext != null) {
			IDebuggerHandler debugger = ScriptHub.getDebugger();

			if (debugger == null) {
				Logger.error("Unable to debug scriptold, no debugger registered.");
				this.opcontext.complete();
			}
			else {
				// so debugging don't timeout
				this.opcontext.getTask().withTimeout(0).withDeadline(0);

				debugger.startDebugger(this.opcontext);
			}
		}
			*/
	}


	@Override
	public TaskContext deepCopy() {
		TaskContext cp = new TaskContext(this.task);
		this.doCopy(cp);
		// TODO copy fields?
		return cp;
	}

	public void run() {
		try {
			OperationContext.set(this);
			
			if (this.started == -1) {
				
				if (this.isFromWorkQueue)
					Logger.infoTr(153, this.task.getId());
				else
					Logger.traceTr(153, this.task.getId());
					
				Logger.traceTr(144, WorkHub.getTopicOrDefault(this));
				
				/* TODO restore for Work Queue feature
				// if this is a queue task then mark it started
				if (this.task.isFromWorkQueue()) {
					FuncResult<String> k = Hub.instance.getWorkQueue().startWork(this.task.getWorkId());
					
					if (k.hasErrors()) {
						// TODO replace with hub events
						Hub.instance.getWorkQueue().sendAlert(179, this.task.getId(), k.getMessage());
						
						this.errorTr(179, this.task.getId(), k.getMessage());
						this.complete();
						return;
					}
					
					this.task.incCurrentTry();
					this.task.withRunId(k.getResult());
				}
				*/
				
				this.started = this.lastclaimed = System.currentTimeMillis();
				
				// task start before work
				this.getController().fireEvent(this, OperationConstants.START_TASK, null);
			}
			
			this.touch();
			
			IWork work = this.workchain.peekFirst();
			
			if (work == null) {
				Logger.errorTr(217, this);
				this.complete();
				return;
			}
			
			work.run(this);
		}
		catch (Exception x) {
			Logger.errorTr(155, this.task.getId(), x);
			
			IWork work = this.workchain.peekFirst();
			
			if (work != null)			
				System.out.println("Work pool caught exception: " + work.getClass());
			
			System.out.println("Stack Trace: ");
			x.printStackTrace();
			
			this.complete();
		}
		finally {
			OperationContext.set(null);
		}
	}		
	
	/**
	 * @param lvl level of message
	 * @param code for message
	 * @param msg text of message
	 * @param tags of message
	 */
	public void log(DebugLevel lvl, long code, String msg, String... tags) {
		// must be some sort of message
		if (StringUtil.isEmpty(msg))
			msg = "Unknown message";
		
		RecordStruct entry = new RecordStruct()
			.with("Occurred", TimeUtil.now())
			.with("Level", lvl.toString())
			.with("TaskId", this.task.getId())
			.with("Code", code)
			.with("Message", msg);
		
		if (tags.length > 0)
			entry.with("Tags", ListStruct.list((Object[])tags));
		
		this.getController().log(this,entry, lvl);
		
		if ((this.getExitCode() == 0) && (lvl == DebugLevel.Error)) {
			this
					.with("ExitCode", code)
					.with("ExitMessage", msg);
		}
	}
	
	/**
	 * @param lvl level of message
	 * @param code for message
	 * @param params parameters to the message string
	 */
	public void logTr(DebugLevel lvl, long code, Object... params) {
		String msg = this.tr("_code_" + code, params);
		
		RecordStruct entry = new RecordStruct()
			.with("Occurred", TimeUtil.now())
			.with("Level", lvl.toString())
			.with("TaskId", this.task.getId())
			.with("Code", code)
			.with("Message", msg);
		
		this.getController().log(this, entry, lvl);
		
		if ((this.getExitCode() == 0) && (lvl == DebugLevel.Error)) {
			this
					.with("ExitCode", code)
					.with("ExitMessage", msg);
		}
	}
	
	// is there an attached debugger?
	public boolean hasDebugger() {
		return false;
	}
	
	/**
	 * Overrides any previous return codes and messages
	 * 
	 * @param code code for message
	 * @param msg message
	 */
	public void setExitCode(long code, String msg) {
		if (StringUtil.isNotEmpty(msg))
			this.log(DebugLevel.Info, code, msg, "Exit");
		else 
			this.boundary("Code", code + "", "Exit");
		
		this
				.with("ExitCode", code)
				.with("ExitMessage", msg);
		
		if (StringUtil.isNotEmpty(msg))
			HubLog.log(this.getController().getSeqNumber(), DebugLevel.Info, code, msg, "Exit");
		else
   			HubLog.boundary(this.getController().getSeqNumber(), 0, "Code", code + "", "Exit");
	}
	
    // let Logger translate to the language of the log file - let tasks translate to their own logs in
    // the language of the context 
    
	public void setExitCodeTr(long code, Object... params) {
		String msg = this.tr("_code_" + code, params);
		
		if (StringUtil.isNotEmpty(msg))
    		this.log(DebugLevel.Info, code, msg);
		else 
			this.boundary("Code", code + "", "Exit");
		
		this
				.with("ExitCode", code)
				.with("ExitMessage", msg);
		
		String smsg = ResourceHub.trSys("_code_" + code, params);
		
		if (StringUtil.isNotEmpty(smsg))
			HubLog.log(this.getController().getSeqNumber(), DebugLevel.Info, code, smsg);
		else
   			HubLog.boundary(this.getController().getSeqNumber(), 0, "Code", code + "", "Exit");
	}

	public void clearExitCode() {
		this.setExitCode(0, null);
	}
	
	public boolean hasExitErrors() {
		return (this.getExitCode() != 0);
	}
	
	public void resumeWith(IWork work) {
		this.workchain.addFirst(work);
		this.resume();
	}
	
	public void resume() {
		OperationContext oc = OperationContext.getOrNull();
		
		try {
			OperationContext.set(this);
			this.touch();
			WorkHub.submit(this);
		}
		finally {
			OperationContext.set(oc);
		}
	}

	public void kill(String msg) {
		OperationContext oc = OperationContext.getOrNull();
		
		try {
			OperationContext.set(this);
			
			Logger.error(msg);
			this.kill();
		}
		finally {
			OperationContext.set(oc);
		}
	}
	
	/**
	 * @param code code for message
	 * @param msg message
	 */
	public void kill(long code, String msg) {
		OperationContext oc = OperationContext.getOrNull();
		
		try {
			OperationContext.set(this);
			
			Logger.error(code, msg);
			this.kill();
		}
		finally {
			OperationContext.set(oc);
		}
	}

	public void killTr(long code, Object... params) {
		OperationContext oc = OperationContext.getOrNull();
		
		try {
			OperationContext.set(this);
			
			Logger.errorTr(code, params);
			this.kill();
		}
		finally {
			OperationContext.set(oc);
		}
	}
	
	public void kill() {
		// this is not totally thread safe but should work most of the time as kill is extremely rarely in a race with complete
		boolean completed = this.completedflag.get();
		
		// already true then someone else completed first
		if (completed)
			return;
		
		OperationContext oc = OperationContext.getOrNull();
		
		try {
			OperationContext.set(this);

			// collect inactive before error logging, logging updates the activity
			boolean inactive = this.isInactive();
			boolean overdue = this.isOverdue();
			
			if (! inactive && ! overdue) {
				Logger.errorTr(196, this.task);		// TODO switch to "subtask killed"
				
				// on a killed chain, remove the top and resume to run the next
				this.workchain.pollFirst();
				
				// now see if there is more work yet to be done
				IWork work = this.workchain.peekFirst();
				
				/*
				// more advanced workers can take the resumeNext to mean something special
				if (work instanceof IChainAwareWork) {
					try {
						((IChainAwareWork) work).resumeNext(this);
					}
					catch (OperatingContextException x) {
						Logger.error("Unable to resume task: " + x);
					}
					
					return;
				}
				*/
				
				// OK to do a standard resume here because parent work was expecting a general resume any way
				// cannot send out
				if (work != null) {
					this.resume();
					return;
				}
				
				// if no chain then we really are done
			}
			
			completed = this.completedflag.getAndSet(true);
			
			// already true then someone else completed first
			if (completed)
				return;
			
			try {
				Logger.errorTr(196, this.task);
				
				if (overdue)
					Logger.errorTr(222, this.task);
				else if (inactive)
					Logger.errorTr(223, this.task);
				
				this.completeWork();
			}
			finally {
				// always mark task completed so it isn't stuck in work pool, thread safe
				WorkHub.complete(this);
			}
		}
		finally {
			OperationContext.set(oc);
		}
	}
	
	// this should only be called in context (task run), outside code should not call this
	public void complete() {
		// check to see if work is really done
		IWork currwork = this.workchain.peekFirst();
		
		if (currwork instanceof IChainAwareWork) {
			if (! ((IChainAwareWork) currwork).isComplete(this)) {
				try {
					((IChainAwareWork) currwork).resumeNext(this);
				}
				catch (OperatingContextException x) {
					Logger.error("Unable to resume task: " + x);
				}
				return;
			}
		}
		
		// on a completed chain, remove the top and resume to run the next
		this.workchain.pollFirst();
		
		if (Logger.isTrace())
			Logger.debug("Completed work chain: " + currwork.getClass().getCanonicalName());
		
		// now see if there is more work yet to be done
		IWork work = this.workchain.peekFirst();
		
		// more advanced workers can take the resumeNext to mean something special
		if (work instanceof IChainAwareWork) {
			try {
				((IChainAwareWork) work).resumeNext(this);
			}
			catch (OperatingContextException x) {
				Logger.error("Unable to resume task: " + x);
			}

			return;
		}
		
		// less advanced workers just run again - if any
		if (work != null) {
			this.resume();
			return;
		}
		
		// if no chain then we really are done
		boolean completed = this.completedflag.getAndSet(true);
		
		// already true then someone else completed first
		if (completed)
			return;
		
		this.completeWork();
	}		
		
	protected void completeWork() {
		OperationContext oc = OperationContext.getOrNull();
		
		try {
			// make sure we complete in the correct context (only worker should call this method)
			OperationContext.set(this);
			
			try {			
				
				// task observers could log still - so before close log
				this.getController().fireEvent(this, OperationConstants.COMPLETED, null);
				
				// task observers stop can/should no longer log
				this.getController().fireEvent(this, OperationConstants.STOP_TASK, null);
				
				/* TODO restore for Work Queue feature
				// if this is a queue task then end it - only if we got an audit it though
				// TODO refine this - if we have a task id but not an audit id we should cleanup the queue...
				// TODO what should we do if not started - (this.started == -1)
				if (this.task.isFromWorkQueue() && this.task.hasAuditId()) {				
					// don't go forward if this no longer holds a claim
					if (! this.updateClaim()) 
						// record only that we ended but not a status or a queue change
						WorkQueue.trackWork(this, true);
					else if (this.hasErrors()) 
						// record failure if errors
						WorkQueue.failWork(this);
					else 
						// otherwise record completed
						WorkQueue.completeWork(this);
				}
				*/
				
				// don't remove temp folder till after record to queue in case the logger needs the folder to read log content from
				
				// TODO what should we do if not started - (this.started == -1)
				if (this.isFromWorkQueue)
					Logger.infoTr(154, this.getExitCode());
				else
					Logger.traceTr(154, this.getExitCode());
			}
			finally {
				// always mark task completed so it isn't stuck in work pool, thread safe
				WorkHub.complete(this);
			}
		}
		finally {
			OperationContext.set(oc);
		}
	}

	@Override
	public String toString() {
		return this.task.getTitle() + " (" + this.task.getId() + ":" + this.slot + ")";
	}
	
	public RecordStruct toStatusReport() {
		return RecordStruct.record()
			.with("Id", this.task.getId())
			.with("Title", this.task.getTitle())
			.with("Tags", this.task.getTags())
			.with("Completed", this.completedflag.get());
		
		// TODO started, last touched/action, code, message, finished...
	}
	
	@Override
	public int hashCode() {
		return this.task.getTitle().hashCode();
	}
	
	public BaseStruct getResult() {
	    return this.getField("Result");
	}

	public void setResult(BaseStruct v) {
	    this.with("Result", v);
	}

	/*
	 * For scripting calls - set the return value (convert to struct if not already) then call complete all at once
	 * @param v
	 */
	public void returnValue(Object v) {
		this.with("Result", v);
		this.complete();
	}
	
	public void returnResult() {
		this.complete();
	}	
	
	public void returnEmpty() {
		this.with("Result", null);
		this.complete();
	}	

	// code methods - TODO override everything from hasErrors to clearErrors 
	// these all now apply only to specific log entries with the task id
	
	// logging methods - TODO override and insert TaskId into the log records

	// progress methods
	
	/**
	 * @return units/percentage of task completed
	 */
	public int getAmountCompleted() {
		return this.progComplete; 
	}
	
	/**
	 * @param v units/percentage of task completed
	 */
	public void setAmountCompleted(int v) { 
		this.progComplete = v; 
		
		this.getController().fireEvent(this, OperationConstants.PROGRESS, OperationConstants.PROGRESS_AMOUNT);
	}

	/**
	 * @return status message about task progress
	 */
	public String getProgressMessage() {
		return this.progMessage; 
	}
	
	/**
	 * @param v status message about task progress
	 */
	public void setProgressMessage(String v) { 
		this.progMessage = v; 
		
		this.getController().fireEvent(this, OperationConstants.PROGRESS, OperationConstants.PROGRESS_MESSAGE);
	}
	
	/**
	 * @param code message translation code
	 * @param params for the message string
	 */
	public void setProgressMessageTr(int code, Object... params) { 
		this.progMessage = this.tr("_code_" + code, params); 
		
		this.getController().fireEvent(this, OperationConstants.PROGRESS, OperationConstants.PROGRESS_MESSAGE);
	}

	/**
	 * @return total steps for this specific task
	 */
	public int getSteps() { 
		return this.progTotalSteps; 
	}
	
	/**
	 * @param v total steps for this specific task
	 */
	public void setSteps(int v) { 
		this.progTotalSteps = v; 
	}

	/**
	 * @return current step within this specific task
	 */
	public int getCurrentStep() { 
		return this.progCurrStep; 
	}
	
	/**
	 * Set step name first, this triggers observers
	 * 
	 * @param step current step number within this specific task
	 * @param name current step name within this specific task
	 */
	public void setCurrentStep(int step, String name) { 
		this.progCurrStep = step; 
		this.progStepName = name; 
		
		this.getController().fireEvent(this, OperationConstants.PROGRESS, OperationConstants.PROGRESS_STEP);
	}
	
	/**
	 * Set step name first, this triggers observers
	 * 
	 * @param name current step name within this specific task
	 */
	public void nextStep(String name) { 
		this.progCurrStep++; 
		this.progStepName = name; 
	
		this.getController().fireEvent(this, OperationConstants.PROGRESS, OperationConstants.PROGRESS_STEP);
	}

	/**
	 * @return name of current step
	 */
	public String getCurrentStepName() { 
		return this.progStepName; 
	}
	
	/**
	 * @param step number of current step
	 * @param code message translation code
	 * @param params for the message string
	 */
	public void setCurrentStepNameTr(int step, int code, Object... params) {
		String name = this.tr("_code_" + code, params);
					
		this.progCurrStep = step; 
		this.progStepName = name; 
	
		this.getController().fireEvent(this, OperationConstants.PROGRESS, OperationConstants.PROGRESS_STEP);
	}    
	
	/**
	 * @param code message translation code
	 * @param params for the message string
	 */
	public void nextStepTr(int code, Object... params) {
		String name = this.tr("_code_" + code, params);
					
		this.progCurrStep++; 
		this.progStepName = name; 
		
		this.getController().fireEvent(this, OperationConstants.PROGRESS, OperationConstants.PROGRESS_STEP);
	}    
	
	/* TODO supports groovy, enhance
	@Override
	public Object invokeMethod(String name, Object arg1) {
		// is really an object array
		Object[] args = (Object[])arg1;
		
		if ("return".equals(name)) {
			if (args.length > 0)
				this.returnValue(args[0]);
			else
				this.returnEmpty();
			
			return null;
		}
		
		return super.invokeMethod(name, arg1);
	}
	*/

	/*
	public RecordStruct status() {
		RecordStruct status = this.task.status();
		
		// TODO some of this may need review
		status.setField("Status", this.completed ? "Completed" : "Running");			
		status.setField("Start", new DateTime(this.started)); 
		status.setField("End", null); 
		status.setField("NodeId", OperationContext.getNodeId());
		
		status.setField("Code", this.getCode());
		status.setField("Message", this.getMessage()); 
		status.setField("Log", this.getContext().getLog());
		status.setField("Progress", this.opcontext.getProgressMessage()); 
		status.setField("StepName", this.opcontext.getCurrentStepName()); 
		status.setField("Completed", this.opcontext.getAmountCompleted()); 
		status.setField("Step", this.opcontext.getCurrentStep()); 
		status.setField("Steps", this.opcontext.getSteps());
		
		return status;
	}
	*/
}
