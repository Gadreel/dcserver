package dcraft.web.adapter;

import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.tenant.Site;
import dcraft.web.IOutputWork;
import dcraft.web.IReviewWork;
import dcraft.web.IWebWorkBuilder;

import java.nio.file.Path;

public class PHPOutputBuilder implements IWebWorkBuilder {
	@Override
	public IOutputWork buildOutputAdapter(Site site, Path file, CommonPath loc, String view) throws OperatingContextException {
		IOutputWork work = new PHPOutputAdapter();
		work.init(site, file, loc, view);
		return work;
	}

	@Override
	public IReviewWork buildReviewAdapter(Site site, Path file, CommonPath loc, String view) throws OperatingContextException {
		return null;
	}
}
