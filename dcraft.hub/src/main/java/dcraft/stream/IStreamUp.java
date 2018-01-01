package dcraft.stream;

import dcraft.hub.op.OperatingContextException;

public interface IStreamUp extends IStream {
	void read() throws OperatingContextException;
}
