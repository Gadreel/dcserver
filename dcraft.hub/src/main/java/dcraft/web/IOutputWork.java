package dcraft.web;

import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.task.IWork;
import dcraft.tenant.Site;

import java.nio.file.Path;

public interface IOutputWork extends IWork {
	Path getFile();
	CommonPath getPath();
	void init(Site site, Path file, CommonPath loc, String view) throws OperatingContextException;
}
