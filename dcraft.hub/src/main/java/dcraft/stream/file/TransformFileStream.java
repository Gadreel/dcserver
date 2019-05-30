package dcraft.stream.file;

import dcraft.hub.op.OperatingContextException;
import dcraft.stream.IStreamUp;
import dcraft.stream.ReturnOption;

abstract public class TransformFileStream extends BaseFileStream implements IStreamUp, IFileStreamConsumer {
	@Override
	public void read() throws OperatingContextException {
		if (this.handlerFlush() == ReturnOption.CONTINUE)
			this.upstream.read();
	}
}
