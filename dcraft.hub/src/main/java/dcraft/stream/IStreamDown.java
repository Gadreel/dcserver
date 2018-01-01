package dcraft.stream;

import dcraft.hub.op.OperatingContextException;

public interface IStreamDown<T> extends IStream {
	ReturnOption handle(T slice) throws OperatingContextException;
}
