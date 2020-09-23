package dcraft.web;

import dcraft.db.util.DocumentIndexBuilder;
import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.script.Script;
import dcraft.task.IWork;
import dcraft.tenant.Site;
import dcraft.util.MimeInfo;

import java.nio.file.Path;

public interface IReviewWork extends IWork {
	CommonPath getPath();
	MimeInfo getMime();
	Script getScript();

	void init(Site site, Path file, CommonPath loc, String view) throws OperatingContextException;
}
