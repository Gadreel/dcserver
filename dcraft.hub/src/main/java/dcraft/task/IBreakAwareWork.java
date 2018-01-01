package dcraft.task;

public interface IBreakAwareWork extends IParentAwareWork {
	void flagBreak();
	void flagContinue();
}
