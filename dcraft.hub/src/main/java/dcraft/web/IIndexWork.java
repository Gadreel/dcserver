package dcraft.web;

import dcraft.db.util.DocumentIndexBuilder;
import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.task.IWork;
import dcraft.tenant.Site;

import java.nio.file.Path;

public interface IIndexWork extends IWork {
	CommonPath getPath();
	DocumentIndexBuilder getIndexer();
	void init(Site site, Path file, CommonPath loc, String view) throws OperatingContextException;
}