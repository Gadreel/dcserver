package dcraft.task;

import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;

public interface IDebuggableWork extends IWork {
	void debugStack(ListStruct dumpList);
	void collectDebugRecord(RecordStruct rec);
}
