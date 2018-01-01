package dcraft.stream.record;

import dcraft.stream.IStreamDest;
import dcraft.struct.RecordStruct;

public interface IRecordStreamDest extends IRecordStreamConsumer, IStreamDest<RecordStruct> {
}
