package dcraft.task;

public interface ICancelAwareWork extends IParentAwareWork {
	void cancel();
}
