package dcraft.stream.record;

import com.sun.javafx.font.directwrite.RECT;
import dcraft.hub.op.OperatingContextException;
import dcraft.stream.IStreamDest;
import dcraft.stream.IStreamDown;
import dcraft.struct.RecordStruct;

import java.util.function.Consumer;

public interface IRecordStreamConsumer extends IStreamDown<RecordStruct> {
	IStreamDown<RecordStruct> withTabulator(Consumer<RecordStruct> v) throws OperatingContextException;
}
