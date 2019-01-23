package dcraft.stream.file;

import dcraft.filestore.FileDescriptor;
import dcraft.filestore.FileStoreFile;
import dcraft.hub.op.OperatingContextException;
import dcraft.stream.IStreamDown;

import java.util.function.Consumer;

public interface IFileStreamConsumer extends IStreamDown<FileSlice> {
}
