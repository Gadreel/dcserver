package dcraft.hub.op;

import java.util.concurrent.atomic.AtomicBoolean;

import dcraft.log.Logger;
import dcraft.stream.IStreamDown;
import dcraft.stream.IStreamUp;
import dcraft.stream.StreamFragment;
import dcraft.struct.Struct;
import dcraft.task.ISchedule;
import dcraft.task.IWork;
import dcraft.task.Task;
import dcraft.task.TaskContext;
import dcraft.task.TaskHub;
import dcraft.util.cb.TimeoutPlan;

abstract public class OperationOutcome<T> extends OperationMarker {
	// keep only as long as necessary, clear after outcome 
	protected OperationContext opcontext = null;
	protected AtomicBoolean calledflag = new AtomicBoolean();
	protected ISchedule timeout = null;
	protected T value = null;
	
	public OperationOutcome() throws OperatingContextException {
		this.opcontext = OperationContext.getOrNull();
		
		if (this.opcontext == null)
			throw new OperatingContextException("OperationOutcome created without a context to run in.");
		
		this.contextid = this.opcontext.getOpId();
    	this.msgStart = this.opcontext.getController().logMarker();
	}
	
	public OperationOutcome(TimeoutPlan plan) throws OperatingContextException {
		this();
		
		Task timeouttask = Task.ofSubContext()
			.withTitle("OperationOutcome with a timeout")
			.withWork(new IWork() {
				@Override
				public void run(TaskContext task) {
					OperationOutcome.this.abandon();
					task.returnEmpty();
				}
			});
		
		this.timeout = TaskHub.scheduleIn(timeouttask, plan.getSeconds());
	}
	
	public T getResult() {
	    return this.value;
	}
		 
	public void setResult(T v) {
	    this.value = v;
	}
	
	public boolean isNotEmptyResult() {
		return ! Struct.objectIsEmpty(this.value);
	}
	
	public boolean isEmptyResult() {
		return Struct.objectIsEmpty(this.value);
	}

	abstract public void callback(T result) throws OperatingContextException;
	
	public void markStart() throws OperatingContextException {
		this.msgStart = this.getOperationContext().getController().logMarker();
	}
	
	public void reset() throws OperatingContextException {
		this.calledflag.set(false);		
		this.opcontext = this.getOperationContext();	
	}
	
	// override if need to do something on timeout/giveup on operation
	// return true if timeout occurred, false if already completed
	public boolean abandon() {
		boolean called = this.calledflag.getAndSet(true);
		
		// already true then someone else called first
		if (called)
			return false;

		Logger.errorTr(218, this.opcontext);

		this.completeWork(null);
		
		return true;
	}
	
	public void useContext() {
		OperationContext.set(this.opcontext);
	}
	
	@Override
	public OperationContext getOperationContext() throws OperatingContextException {
		OperationContext ctx = this.opcontext;
		
		if (ctx != null)
			return ctx;
		
		// restore from current context, local will be null here if used correctly
		return super.getOperationContext();
	}
	
	public void returnResult() {
		this.returnValue(this.value);
	}
	
	public void returnEmpty() {
		this.returnValue(null);
	}
	
	public void returnValue(T v) {
		boolean called = this.calledflag.getAndSet(true);
		
		// already true then someone else called first
		if (called)
			return;

		this.completeWork(v);
	}
	
	protected void completeWork(T v) {
	    this.setResult(v);
		
		// be sure we restore the previous context, if any
		OperationContext ctx = OperationContext.getOrNull();
		
		try {
			OperationContext.set(this.opcontext);
			
			if (this.timeout != null)
				this.timeout.cancel();
			
			// just in case cancel changes OC
			OperationContext.set(this.opcontext);
			
			this.close();
			
			// just in case close changes OC
			OperationContext.set(this.opcontext);
			
			this.callback(this.value);
		}
		catch (Exception x) {
			Logger.error("Callback failure: " + x);
		}
		finally {
			OperationContext.set(ctx);
			
			this.opcontext = null;
		}
	}	
	
	// TODO move to utility
	/*
	public Message toLogMessage() throws OperatingContextException {
		return (Message) new Message()
			.with("Messages", this.getMessages())
			.with("Body", this.getResult());
	}
	*/
}
