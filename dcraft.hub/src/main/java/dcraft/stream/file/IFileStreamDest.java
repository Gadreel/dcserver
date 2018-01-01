package dcraft.stream.file;

import dcraft.stream.IStreamDest;
import dcraft.stream.IStreamDown;

public interface IFileStreamDest extends IFileStreamConsumer, IStreamDest<FileSlice> {
}
