package dcraft.cms.util;

import dcraft.hub.op.OperatingContextException;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;

public interface GalleryImageConsumer {
	void accept(RecordStruct meta, RecordStruct show, RecordStruct img) throws OperatingContextException;
}
