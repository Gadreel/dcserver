package dcraft.interchange.lightspeed;

import dcraft.db.proc.ExpressionResult;
import dcraft.hub.op.OperatingContextException;
import dcraft.struct.RecordStruct;

abstract public class LightspeedRecordConsumer {
	abstract public ExpressionResult accept(RecordStruct record) throws OperatingContextException;
}
