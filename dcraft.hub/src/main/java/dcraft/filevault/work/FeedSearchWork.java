package dcraft.filevault.work;

import dcraft.cms.util.FeedUtil;
import dcraft.db.fileindex.FileIndexAdapter;
import dcraft.db.util.DocumentIndexBuilder;
import dcraft.filestore.CommonPath;
import dcraft.filevault.Vault;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationMarker;
import dcraft.log.Logger;
import dcraft.script.Script;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.ChainWork;
import dcraft.task.IWork;
import dcraft.task.TaskContext;
import dcraft.tenant.Site;
import dcraft.tenant.WebFindResult;
import dcraft.web.HtmlMode;
import dcraft.web.IReviewWork;
import dcraft.web.WebController;
import dcraft.web.adapter.DynamicReviewAdapter;
import dcraft.web.adapter.MarkdownReviewAdapter;
import dcraft.web.ui.UIUtil;
import dcraft.web.ui.inst.Body;
import dcraft.web.ui.inst.Html;
import dcraft.web.ui.inst.cms.IncludeFeed;
import dcraft.xml.XElement;

import java.nio.file.Path;
import java.util.List;

/*
	Task must be run with a WebController for controller, but not need to prep the WC vars
 */
public class FeedSearchWork extends ChainWork {
	static public FeedSearchWork of(Vault vault, CommonPath path) {
		FeedSearchWork work = new FeedSearchWork();
		work.vault = vault;
		work.path = path;
		return work;
	}
	
	protected Vault vault = null;
	protected CommonPath path = null;

	@Override
	protected void init(TaskContext taskctx) throws OperatingContextException {
		List<String> locales = taskctx.getResources().getLocale().getAlternateLocales();

		locales.add(taskctx.getResources().getLocale().getDefaultLocale());

		for (String locale : locales)
			this.then(FeedSearchLocaleWork.of(this.vault, this.path, locale));

		this
			.then(new IWork() {
				@Override
				public void run(TaskContext taskctx) throws OperatingContextException {
					taskctx.returnEmpty();
				}
			});
	}
}
