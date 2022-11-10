package dcraft.mail;

import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.task.IWork;

import java.nio.file.Path;

public interface IEmailOutputWork extends IWork {
	CommonPath getPath();

	// not guaranteed to be in proper context
	void init(Path file, CommonPath loc, String view) throws OperatingContextException;
}
