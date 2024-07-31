package dcraft.core.doc;

import dcraft.hub.op.OperatingContextException;
import dcraft.struct.RecordStruct;
import dcraft.task.IWork;

public interface IDocOutputWork extends IWork {
	// not guaranteed to be in proper context
	void init(RecordStruct request) throws OperatingContextException;
}
