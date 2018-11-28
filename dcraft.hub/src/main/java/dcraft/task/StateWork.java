package dcraft.task;

import java.util.ArrayList;
import java.util.List;

import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;

abstract public class StateWork implements IChainAwareWork {
	protected List<StateWorkStep> steps = new ArrayList<>();
	protected StateWorkStep last = null;
	protected StateWorkStep current = null;
	protected StateWorkStep resumeStep = null;
	protected boolean failOnErrors = true;
	protected boolean stopFlag = false;
	
	public StateWork withStep(StateWorkStep v) {
		this.steps.add(v);
		return this;
	}
	
	public StateWork withSteps(StateWorkStep... v) {
		for (StateWorkStep s : v)
			this.steps.add(s);
		
		return this;
	}
	
	public StateWork withoutFailOnErrors() {
		this.failOnErrors = false;
		return this;
	}
	
	public StateWork withFailOnErrors() {
		this.failOnErrors = true;
		return this;
	}
	
	abstract public void prepSteps(TaskContext trun) throws OperatingContextException;
	
	@Override
	public void run(TaskContext trun) throws OperatingContextException {
		// check here because sub work (trun.resumeWith(work);) can cause an error and resume here
		if (this.failOnErrors && trun.hasExitErrors()) {
			trun.kill();
			return;
		}
		
		if (this.steps.size() == 0) {
			this.prepSteps(trun);
			
			trun.setSteps(this.steps.size());
			this.current = this.steps.get(0);
		}
		
		// null means we are still processing something
		if (this.current == null)
			return;
		
		trun.setCurrentStep(this.steps.indexOf(this.current) + 1, this.current.getTitle());
		
		this.last = this.current;
		
		try {
			this.transition(trun, this.current, this.current.runStep(trun));
		}
		catch (Exception x) {
			StateWorkStep s = this.current;
			
			System.out.println("error: " + trun.getTask().getTitle());
			
			x.printStackTrace(System.out);
			
			if (s == null)
				Logger.error("Unexpected StateWork exception - no current step");
			else
				Logger.error("Unexpected StateWork exception for: " + s.getTitle());
			
			trun.kill("Message: " + x);
		}
	}
	
	public void transition(TaskContext trun, StateWorkStep to) {
		this.transition(trun, this.last, to);
	}
	
	public void transition(TaskContext trun, StateWorkStep from, StateWorkStep to) {
		if (this.failOnErrors && trun.hasExitErrors()) {
			trun.kill();
			return;
		}
		
		// must not do anything on WAIt or risk race conditions
		if (to == StateWorkStep.WAIT)
			return;
		
		if (to == StateWorkStep.STOP) {
			this.stopFlag = true;
			trun.complete();
			return;
		}
		
		if (to == StateWorkStep.REPEAT) {
			trun.resume();
			return;
		}
		
		if (to == StateWorkStep.NEXT) {
			int topos = this.steps.indexOf(from) + 1;
			
			if (topos < this.steps.size()) {
				to = this.steps.get(topos);
			}
			else {
				trun.complete();
				return;
			}
		}
		
		this.last = from;
		this.current = to;

		this.transitionEvent(trun, from, to);
		
		trun.resume();
	}
	
	public StateWorkStep chainThenNext(TaskContext trun, IWork work) {
		return this.chainThen(trun, work, StateWorkStep.NEXT);
	}
	
	public StateWorkStep chainThenRepeat(TaskContext trun, IWork work) {
		return this.chainThen(trun, work, StateWorkStep.REPEAT);
	}
	
	public StateWorkStep chainThen(TaskContext trun, IWork work, StateWorkStep next) {
		this.resumeStep = next;
		trun.resumeWith(work);
		return StateWorkStep.WAIT;
	}
	
	@Override
	public void resumeNext(TaskContext taskctx) {
		this.transition(taskctx, this.resumeStep);
	}
	
	@Override
	public boolean isComplete(TaskContext taskctx) {
		// if a step returns a STOP
		if (this.stopFlag)
			return true;
		
		// if on last step when complete is called
		int topos = this.steps.indexOf(this.last) + 1;
		
		return (topos == this.steps.size());
	}
	
	public void resumeNextStep(TaskContext trun) {
		if (this.failOnErrors && trun.hasExitErrors()) {
			trun.kill();
			return;
		}
		
		int topos = this.steps.indexOf(this.last) + 1;
		
		if (topos < this.steps.size()) {
			this.current = this.steps.get(topos);
		}
		else {
			trun.complete();
			return;
		}
		
		this.transitionEvent(trun, this.last, this.current);
		
		trun.resume();
	}
	
	public void transitionEvent(TaskContext trun, StateWorkStep from, StateWorkStep to) {
		// real work can override
	}
}
