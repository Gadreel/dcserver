package dcraft.db.proc;

import java.util.function.Function;

import dcraft.db.DbServiceRequest;
import dcraft.hub.op.OperatingContextException;
import dcraft.struct.RecordStruct;

public interface ICollector {
	void collect(DbServiceRequest task, RecordStruct collector, Function<Object,Boolean> uniqueConsumer) throws OperatingContextException;
}
