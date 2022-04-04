package dcraft.filevault.work;

import dcraft.cms.util.FeedUtil;
import dcraft.db.BasicRequestContext;
import dcraft.db.IConnectionManager;
import dcraft.db.fileindex.FileIndexAdapter;
import dcraft.db.util.DocumentIndexBuilder;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.local.LocalStoreFile;
import dcraft.filevault.DepositHub;
import dcraft.filevault.Vault;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationMarker;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.hub.resource.KeyRingResource;
import dcraft.log.Logger;
import dcraft.script.Script;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.task.*;
import dcraft.tenant.Site;
import dcraft.util.StringUtil;
import dcraft.util.chars.Utf8Encoder;
import dcraft.util.pgp.ClearsignUtil;
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
import org.bouncycastle.openpgp.PGPSecretKeyRing;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class FeedSearchLocaleWork extends StateWork {
	static public FeedSearchLocaleWork of(Vault vault, CommonPath path, String locale) {
		FeedSearchLocaleWork work = new FeedSearchLocaleWork();
		work.vault = vault;
		work.path = path;
		work.locale = locale;
		return work;
	}

	protected Vault vault = null;
	protected CommonPath path = null;
	protected String locale = null;

	protected IReviewWork output = null;

	protected StateWorkStep init = null;
	protected StateWorkStep render = null;
	protected StateWorkStep process = null;
	protected StateWorkStep done = null;

	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this
				.withStep(this.init = StateWorkStep.of("Initialize", this::init))
				.withStep(this.render = StateWorkStep.of("Render Feed", this::render))
				.withStep(this.process = StateWorkStep.of("Index Feed", this::process))
				.withStep(this.done = StateWorkStep.of("Done", this::done));
	}
	
	public StateWorkStep init(TaskContext taskctx) throws OperatingContextException {
		try (OperationMarker om = OperationMarker.create()) {
			taskctx.setLocale(this.locale);

			Site webSite = taskctx.getSite();
			CommonPath path = this.path;

			path = FeedUtil.translateToWebPath(path);

			if (path == null) {
				Logger.info("Feed type does not index, skipping");
				return this.done;
			}

			if (path.getFileName().endsWith(".html"))
				path = path.getParent().resolve(path.getFileName().substring(0, path.getFileName().length() - 5));

			if (Logger.isDebug())
				Logger.debug("Site: " + webSite.getAlias());

			if (Logger.isDebug())
				Logger.debug("Translating path: " + path);

			if (path.isRoot()) {
				path = webSite.getHomePath();
				//req.with("Path", path.toString());
			}

			if (Logger.isDebug())
				Logger.debug("Process path: " + path);

			// translate above should take us home for root
			if (path.isRoot()) {
				Logger.error("Unable to search root path");
				return this.done;
			}

			// try with path case as is (should be lowercase anyway)
			this.output = webSite.webFindReviewFile(path, null);

			if (om.hasErrors()) {
				Logger.error("Problem finding web page file");
				return this.done;
			}

			// try with lowercase path
			if (this.output == null) {
				CommonPath tpath = CommonPath.from(path.toString().toLowerCase());

				this.output = webSite.webFindReviewFile(path, null);

				if (this.output != null)
					path = tpath;
			}

			if (om.hasErrors()) {
				Logger.error("Problem finding web page file, or file missing.");
				return this.done;
			}

			// try non-pages - feed
			if (this.output == null) {
				Path fspath = webSite.findSectionFile("feeds", path.toString() + ".html", null);

				if (fspath == null) {
					fspath = webSite.findSectionFile("feeds", path.toString().toLowerCase() + ".html", null);

					if (fspath != null)
						path = CommonPath.from(path.toString().toLowerCase());
				}

				if (fspath != null) {
					String filename = fspath.getFileName().toString();

					HtmlMode hmode = webSite.getHtmlMode();

					// currently only supports Dynamic
					if (filename.endsWith(".html")) {
						if ((hmode == HtmlMode.Dynamic) || (hmode == HtmlMode.Strict)) {
							Script script = Script.of(
									Html.tag().with(
											Body.tag().with(
													IncludeFeed.tag()
															.attr("Meta", "true")
															.attr("Name", path.getName(0))
															.attr("Path", path.subpath(1).toString())
											)
									),
									null
							);

							this.output = new DynamicReviewAdapter().withSource(script);
						}
					} else if (filename.endsWith(".md")) {
						this.output = new MarkdownReviewAdapter();
					}

					if (this.output != null)
						this.output.init(webSite, fspath, path, null);
				}
			}

			if (om.hasErrors() || (output == null)) {
				Logger.error("Problem finding web page file, or file missing.");
				return this.done;
			}

			return StateWorkStep.NEXT;
		}
		catch (Exception x) {
			Logger.error("Unable to process web file: " + x);
			return this.done;
		}
	}

	public StateWorkStep render(TaskContext taskctx) throws OperatingContextException {
		WebController wctrl = (WebController) taskctx.getController();

		wctrl.initSearch(path);

		RecordStruct req = wctrl.getFieldAsRecord("Request");

		req.with("Path", path.toString()); //  output.getPath().toString());

		if (Logger.isDebug())
			Logger.debug("Executing adapter: " + output.getClass().getName());

		return this.chainThenNext(taskctx, this.output);
	}

	public StateWorkStep process(TaskContext taskctx) throws OperatingContextException {
		if (output.getScript() == null)
			return this.done;

		XElement root = output.getScript().getXml();

		DocumentIndexBuilder indexer = DocumentIndexBuilder.index(this.locale);

		indexer.setTitle(root.selectFirst("head").selectFirst("title").getText());

		for (XElement meta : root.selectFirst("head").selectAll("meta")) {
			if ("robots".equals(meta.getAttribute("name"))) {
				indexer.setDenyIndex(meta.getAttribute("content", "index").contains("noindex"));
			}
			else if ("description".equals(meta.getAttribute("name"))) {
				indexer.setSummary(meta.getAttribute("content"));
			}
		}

		if (root.hasNotEmptyAttribute("Badges")) {
			String[] tags = root.getAttribute("Badges").split(",");

			indexer.setBadges(ListStruct.list(tags));
		}

		if (root.hasNotEmptyAttribute("SortHint")) {
			indexer.setSortHint(root.getAttribute("SortHint"));
		}

		UIUtil.indexFinishedDocument(root.selectFirst("body"), indexer);

		indexer.endSection();	// just in case

		IConnectionManager connectionManager = ResourceHub.getResources().getDatabases().getDatabase();

		FileIndexAdapter fileIndexAdapter = FileIndexAdapter.of(BasicRequestContext.of(connectionManager.allocateAdapter()));

		fileIndexAdapter.indexSearch(this.vault, this.path, indexer);

		return StateWorkStep.NEXT;
	}
	
	public StateWorkStep done(TaskContext trun) throws OperatingContextException {
		return StateWorkStep.NEXT;
	}
}
