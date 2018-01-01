package dcraft.task;

public interface IParentAwareWork extends IWork {
	IParentAwareWork withParent(IParentAwareWork v);
	IParentAwareWork getParent();
}
