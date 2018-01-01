package dcraft.stream;

import dcraft.filestore.FileStoreFile;
import dcraft.hub.op.OperatingContextException;

public interface IStreamDest<T> extends IStreamDown<T> {
	void execute() throws OperatingContextException;
}
