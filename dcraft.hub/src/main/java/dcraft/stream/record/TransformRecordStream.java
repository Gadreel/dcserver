package dcraft.stream.record;

import dcraft.hub.op.OperatingContextException;
import dcraft.stream.IStreamDown;
import dcraft.stream.IStreamUp;
import dcraft.stream.ReturnOption;
import dcraft.struct.RecordStruct;

import java.util.function.Consumer;

abstract public class TransformRecordStream extends BaseRecordStream implements IStreamUp, IRecordStreamConsumer {
	protected Consumer<RecordStruct> tabulator = null;
	
	@Override
	public IStreamDown<RecordStruct> withTabulator(Consumer<RecordStruct> v) throws OperatingContextException {
		this.tabulator = v;
		return this;
	}
	
	@Override
	public void read() throws OperatingContextException {
		if (this.handlerFlush() == ReturnOption.CONTINUE)
			this.upstream.read();
	}
}
