package dcraft.task;

import dcraft.hub.op.OperatingContextException;

public interface IChainAwareWork extends IWork {
	void resumeNext(TaskContext taskctx) throws OperatingContextException;
	boolean isComplete(TaskContext taskctx);
}
