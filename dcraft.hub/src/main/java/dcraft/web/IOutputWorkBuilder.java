package dcraft.web;

import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.task.IWork;
import dcraft.tenant.Site;

import java.nio.file.Path;

public interface IOutputWorkBuilder {
	IOutputWork buildAdapter(Site site, Path file, CommonPath loc, String view) throws OperatingContextException;
}
