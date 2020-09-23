package dcraft.cms.feed.work;

import dcraft.cms.feed.db.FeedUtilDb;
import dcraft.cms.feed.db.HistoryFilter;
import dcraft.cms.util.FeedUtil;
import dcraft.db.DbServiceRequest;
import dcraft.db.proc.filter.CurrentRecord;
import dcraft.db.proc.filter.Unique;
import dcraft.db.tables.TablesAdapter;
import dcraft.db.util.DbUtil;
import dcraft.filestore.CommonPath;
import dcraft.filevault.Vault;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationMarker;
import dcraft.log.Logger;
import dcraft.script.Script;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.ChainWork;
import dcraft.task.IWork;
import dcraft.task.TaskContext;
import dcraft.tenant.Site;
import dcraft.util.StringUtil;
import dcraft.web.IReviewWork;
import dcraft.web.WebController;
import dcraft.web.ui.inst.IReviewAware;
import dcraft.xml.XElement;
import dcraft.xml.XNode;

import java.util.HashMap;
import java.util.Map;

/*
	Task must be run with a WebController for controller, but not need to prep the WC vars
 */
public class FeedFileReviewWork extends ChainWork {
	static public FeedFileReviewWork of(Vault vault, CommonPath path) {
		FeedFileReviewWork work = new FeedFileReviewWork();
		work.vault = vault;
		work.path = path;
		return work;
	}
	
	protected Vault vault = null;
	protected CommonPath path = null;

	@Override
	protected void init(TaskContext taskctx) throws OperatingContextException {
		try (OperationMarker om = OperationMarker.create()) {
			WebController wctrl = (WebController) taskctx.getController();

			wctrl.initSearch(path);

			RecordStruct req = wctrl.getFieldAsRecord("Request");

			Site webSite = taskctx.getSite();
			CommonPath path = this.path;

			String feed = path.getName(0);

			path = FeedUtil.translateToWebPath(path);

			if (path == null) {
				Logger.info("Feed type does not index, skipping");
				taskctx.returnEmpty();
				return;
			}
			
			if (path.getFileName().endsWith(".html"))
				path = path.getParent().resolve(path.getFileName().substring(0, path.getFileName().length() - 5));

			if (Logger.isDebug())
				Logger.debug("Site: " + webSite.getAlias());
			
			if (Logger.isDebug())
				Logger.debug("Translating path: " + path);
			
			if (path.isRoot()) {
				path = webSite.getHomePath();
				req.with("Path", path.toString());
			}
			
			if (Logger.isDebug())
				Logger.debug("Process path: " + path);
			
			// translate above should take us home for root
			if (path.isRoot()) {
				Logger.error("Unable to search root path");
				return;
			}
			
			// try with path case as is (should be lowercase anyway)
			IReviewWork output = webSite.webFindReviewFile(path, null);
			
			if (om.hasErrors()) {
				Logger.error("Problem finding web page file");
				return;
			}
			
			// try with lowercase path
			if (output == null) {
				path = CommonPath.from(path.toString().toLowerCase());
				output = webSite.webFindReviewFile(path, null);
			}
			
			if (om.hasErrors() || (output == null)) {
				Logger.error("Problem finding web page file, or file missing.");
				return;
			}
			
			req.with("Path", output.getPath().toString());
			
			if (Logger.isDebug())
				Logger.debug("Executing adapter: " + output.getClass().getName());

			final IReviewWork foutput = output;
			final CommonPath fpath = path;
			RecordStruct result = RecordStruct.record();

			this
					.then(output)
					.then(new IWork() {
						@Override
						public void run(TaskContext taskctx) throws OperatingContextException {
							Script script = foutput.getScript();

							XElement root = script.getXml();

							// TODO other locales too
							RecordStruct meta = FeedUtil.metaToInfo(feed, "eng", root);

							ListStruct fields = meta.getFieldAsList("Fields");

							result.with("Fields", fields);

							RecordStruct def = meta.getFieldAsRecord("Definition");

							ListStruct reqlist = ListStruct.list();

							if (def.isNotFieldEmpty("RequiredFields")) {
								ListStruct required = def.getFieldAsList("RequiredFields");

								for (int i = 0; i < required.size(); i++) {
									String fname = required.getItemAsString(i);
									boolean fnd = false;

									for (int b = 0; b < fields.size(); b++) {
										RecordStruct field = fields.getItemAsRecord(b);

										if (fname.equals(field.getFieldAsString("Name"))) {
											fnd = true;
											break;
										}
									}

									if (! fnd)
										reqlist.with(fname);
								}
							}

							result.with("MissingRequiredFields", reqlist);


							ListStruct deslist = ListStruct.list();

							if (def.isNotFieldEmpty("DesiredFields")) {
								ListStruct desired = def.getFieldAsList("DesiredFields");

								for (int i = 0; i < desired.size(); i++) {
									String fname = desired.getItemAsString(i);
									boolean fnd = false;

									for (int b = 0; b < fields.size(); b++) {
										RecordStruct field = fields.getItemAsRecord(b);

										if (fname.equals(field.getFieldAsString("Name"))) {
											fnd = true;
											break;
										}
									}

									if (! fnd)
										deslist.with(fname);
								}
							}

							result.with("MissingDesiredFields", deslist);


							ListStruct dupids = ListStruct.list();

							Map<String, Integer> counters = new HashMap<>();

							checkNode(counters, root, FeedFileReviewWork.this, result);

							//boolean fnd = false;

							for (String id : counters.keySet()) {
								if (counters.get(id) > 1) {
									//fnd = true;
									//break;

									// TODO
									//System.out.println("     **** " + id + " - " + counters.get(id));

									dupids.with(RecordStruct.record()
											.with("Id", id)
											.with("Count", counters.get(id))
									);
								}
							}

							result.with("DuplicateIds", dupids);


							DbServiceRequest request = DbUtil.fakeRequest();
							TablesAdapter db = TablesAdapter.ofNow(request);

							CommonPath xpath = FeedUtilDb.toIndexPath(FeedFileReviewWork.this.path.toString());

							String fid = FeedUtilDb.pathToId(db, xpath, true);
							result.with("RecordPresent", StringUtil.isNotEmpty(fid));

							Unique collector = (Unique) db.traverseIndex(OperationContext.getOrThrow(), "dcmFeedHistory", "dcmDraftPath", xpath.toString(), Unique.unique().withNested(
									CurrentRecord.current().withNested(HistoryFilter.forDraft())));

							result.with("DraftPresent", ! collector.isEmpty());

							/* TODO
			- does it have WAVE check in? developer prompts (pass, skip, incomplete - plus notes - all in database)
				- WAVE link if on prod server (but use the development url)
			- is CMS complete for the page - is everything editable that should be?

							 */

							// finally add work to return result - only after other work has been added

							FeedFileReviewWork.this.then(new IWork() {
								@Override
								public void run(TaskContext taskctx) throws OperatingContextException {
									taskctx.returnValue(result);
								}
							});

							taskctx.returnEmpty();	// causes the additional work to run next
						}
					});
		}
		catch (Exception x) {
			Logger.error("Unable to process web file: " + x);
			return;
		}
	}

	public void checkNode(Map<String, Integer> counters, XElement node, ChainWork morework, RecordStruct result) throws OperatingContextException {
		String id = node.attr("id");

		if (StringUtil.isNotEmpty(id)) {
			if (! counters.containsKey(id))
				counters.put(id, 1);
			else
				counters.put(id, counters.get(id) + 1);
		}

		if (node instanceof IReviewAware) {
			IWork reviewwork = ((IReviewAware) node).buildReviewWork(result);

			if (reviewwork != null)
				morework.then(reviewwork);
		}

		for (int i = 0; i <= node.children(); i++) {
			XNode child = node.getChild(i);

			if (child instanceof XElement) {
				if (child instanceof IReviewAware) {
					if (! ((IReviewAware) child).isReviewHidden()) {
						this.checkNode(counters, (XElement) child, morework, result);
					}
				}
				else {
					this.checkNode(counters, (XElement) child, morework, result);
				}
			}
		}
	}

}
