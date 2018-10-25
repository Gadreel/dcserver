package dcraft.task;

import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;

import java.util.ArrayDeque;
import java.util.Deque;

public class ChainWork implements IChainAwareWork, ICancelAwareWork {
	static public ChainWork of(IWork work) {
		ChainWork chain = new ChainWork();
		chain.workchain.addLast(work);
		return chain;
	}
	
	static public ChainWork of(IWorkBuilder work) {
		ChainWork chain = new ChainWork();
		chain.workchain.addLast(work.toWork());
		return chain;
	}
	
	protected Deque<IWork> workchain = new ArrayDeque<>();
	protected IParentAwareWork parent = null;
	protected boolean init = false;
	
	@Override
	public IParentAwareWork withParent(IParentAwareWork v) {
		this.parent = v;
		return this;
	}
	
	@Override
	public IParentAwareWork getParent() {
		return this.parent;
	}
	
	public ChainWork then(IWork work) {
		if (work instanceof IParentAwareWork)
			((IParentAwareWork) work).withParent(this);
		
		this.workchain.addLast(work);
		return this;
	}
	
	public ChainWork then(IWorkBuilder work) {
		return this.then(work.toWork());
	}
	
	@Override
	public void run(TaskContext taskctx) throws OperatingContextException {
		if (! this.init) {
			this.init(taskctx);
			this.init = true;
		}
		
		IWork work = this.workchain.peekFirst();
		
		if (work != null)
			work.run(taskctx);
		else
			taskctx.complete();
	}
	
	// essentially an abstract method - never add code here
	protected void init(TaskContext taskctx) throws OperatingContextException {
	}
	
	@Override
	public void cancel() {
		IWork work = this.workchain.peekFirst();
		
		if ((work != null) && (work instanceof ICancelAwareWork))
			((ICancelAwareWork) work).cancel();
	}
	
	@Override
	public boolean isComplete(TaskContext taskctx) {
		// check to see if work is really done
		IWork currwork = this.workchain.peekFirst();
		
		if (currwork instanceof IChainAwareWork) {
			boolean deep = ((IChainAwareWork) currwork).isComplete(taskctx);
			
			if (! deep)
				return false;
		}
		
		if (this.workchain.size() > 0)
			return false;
		
		this.init = false;		// so we can run again (in a schedule)
		return true;
	}
	
	@Override
	public void resumeNext(TaskContext taskctx) {
		// check to see if work is really done
		IWork currwork = this.workchain.peekFirst();
		
		if (currwork instanceof IChainAwareWork) {
			if (! ((IChainAwareWork) currwork).isComplete(taskctx)) {
				try {
					((IChainAwareWork) currwork).resumeNext(taskctx);
				}
				catch (OperatingContextException x) {
					Logger.error("Unable to resume chain task: " + x);
				}

				return;
			}
		}
		
		this.workchain.pollFirst();		// remove top
		taskctx.setParams(taskctx.getResult());
		taskctx.resume();
	}
}
