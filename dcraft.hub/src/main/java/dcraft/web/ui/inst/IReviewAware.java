package dcraft.web.ui.inst;

import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.script.work.InstructionWork;
import dcraft.struct.RecordStruct;
import dcraft.task.IWork;
import dcraft.xml.XElement;

public interface IReviewAware {
	default IWork buildReviewWork(RecordStruct result) throws OperatingContextException {
		return null;
	}

	default boolean isReviewHidden() throws OperatingContextException {
		return false;
	}
}
