package dcraft.stream.file;

import dcraft.filestore.FileDescriptor;
import dcraft.filestore.FileStoreFile;
import dcraft.hub.op.OperatingContextException;
import dcraft.scriptold.StackEntry;
import dcraft.stream.IStreamDown;
import dcraft.stream.IStreamUp;
import dcraft.stream.ReturnOption;
import dcraft.xml.XElement;

import java.util.function.Consumer;

abstract public class TransformFileStream extends BaseFileStream implements IStreamUp, IFileStreamConsumer {
	protected Consumer<FileDescriptor> tabulator = null;
	
	@Override
	public IStreamDown<FileSlice> withTabulator(Consumer<FileDescriptor> v) throws OperatingContextException {
		this.tabulator = v;
		return this;
	}
	
	@Override
	public void read() throws OperatingContextException {
		if (this.handlerFlush() == ReturnOption.CONTINUE)
			this.upstream.read();
	}
}
