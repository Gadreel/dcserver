package dcraft.filevault.work;

import dcraft.cms.util.FeedUtil;
import dcraft.db.fileindex.FileIndexAdapter;
import dcraft.db.util.DocumentIndexBuilder;
import dcraft.filestore.CommonPath;
import dcraft.filevault.Vault;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationMarker;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.task.ChainWork;
import dcraft.task.IWork;
import dcraft.task.TaskContext;
import dcraft.tenant.Site;
import dcraft.web.IIndexWork;
import dcraft.web.WebController;

/*
	Task must be run with a WebController for controller, but not need to prep the WC vars
 */
public class FeedSearchWork extends ChainWork {
	static public FeedSearchWork of(Vault vault, CommonPath path, FileIndexAdapter adapter) {
		FeedSearchWork work = new FeedSearchWork();
		work.vault = vault;
		work.path = path;
		work.adapter = adapter;
		return work;
	}
	
	protected Vault vault = null;
	protected CommonPath path = null;
	protected FileIndexAdapter adapter = null;
	protected DocumentIndexBuilder indexer = null;
	
	@Override
	protected void init(TaskContext taskctx) throws OperatingContextException {
		try (OperationMarker om = OperationMarker.create()) {
			WebController wctrl = (WebController) taskctx.getController();

			wctrl.initSearch(path);

			RecordStruct req = wctrl.getFieldAsRecord("Request");

			Site webSite = taskctx.getSite();
			CommonPath path = this.path;

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
				Logger.errorTr(150001);
				return;
			}
			
			// try with path case as is (should be lowercase anyway)
			IIndexWork output = webSite.webFindIndexFile(path, null);
			
			if (om.hasErrors()) {
				Logger.errorTr(150001);
				return;
			}
			
			// try with lowercase path
			if (output == null) {
				path = CommonPath.from(path.toString().toLowerCase());
				output = webSite.webFindIndexFile(path, null);
			}
			
			if (om.hasErrors() || (output == null)) {
				Logger.errorTr(150001);
				return;
			}
			
			req.with("Path", output.getPath().toString());
			
			if (Logger.isDebug())
				Logger.debug("Executing adapter: " + output.getClass().getName());

			// TODO repeat for each Site language
			this.indexer = output.getIndexer();

			this
					.then(output)
					.then(new IndexWriter());
		}
		catch (Exception x) {
			Logger.error("Unable to process web file: " + x);
			return;
		}
	}

	public class IndexWriter implements IWork {
		@Override
		public void run(TaskContext taskctx) throws OperatingContextException {
			adapter.indexSearch(FeedSearchWork.this.vault, FeedSearchWork.this.path, FeedSearchWork.this.indexer);

			taskctx.returnEmpty();
		}
	}
}
