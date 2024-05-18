package dcraft.mail;

import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.task.IWork;

import java.nio.file.Path;

public interface IEmailOutputWork extends IWork {
	CommInfo getInfo();

	// not guaranteed to be in proper context
	void init(CommInfo info) throws OperatingContextException;
}
