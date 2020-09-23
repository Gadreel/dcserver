package dcraft.cms.feed.work;

import dcraft.cms.feed.db.FeedUtilDb;
import dcraft.db.BasicRequestContext;
import dcraft.db.ICallContext;
import dcraft.db.IConnectionManager;
import dcraft.db.fileindex.BasicFilter;
import dcraft.db.fileindex.FileIndexAdapter;
import dcraft.db.fileindex.IFilter;
import dcraft.db.proc.ExpressionResult;
import dcraft.db.proc.filter.CurrentRecord;
import dcraft.db.tables.TablesAdapter;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStore;
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.local.LocalStore;
import dcraft.filevault.FileStoreVault;
import dcraft.filevault.IndexTransaction;
import dcraft.filevault.Vault;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcome;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.IParentAwareWork;
import dcraft.task.StateWork;
import dcraft.task.StateWorkStep;
import dcraft.task.TaskContext;
import dcraft.tenant.Site;
import dcraft.tenant.Tenant;
import dcraft.tenant.TenantHub;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.xml.XElement;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class ReindexFeedWork extends StateWork {
	static public ReindexFeedWork work(String feed, ICallContext request, TablesAdapter db) {
		ReindexFeedWork work = new ReindexFeedWork();
		work.feed = feed;
		work.db = db;

		work.adapter = FileIndexAdapter.of(request);

		return work;
	}
	
	static public ReindexFeedWork work(String feed, ICallContext request) {
		ReindexFeedWork work = new ReindexFeedWork();
		work.feed = feed;

		work.adapter = FileIndexAdapter.of(request);
		work.db = TablesAdapter.ofNow(request);

		return work;
	}

	protected Deque<FileStoreFile> folders = new ArrayDeque<>();
	
	protected Site currentSite = null;
	protected FileStoreVault currentVault = null;
	protected String feed = null;
	protected ZonedDateTime now = TimeUtil.now();
	
	protected StateWorkStep indexFolder = null;
	protected StateWorkStep transaction = null;
	protected StateWorkStep checkFileIndex = null;
	protected StateWorkStep checkFeedIndex = null;
	protected StateWorkStep done = null;
	
	protected FileIndexAdapter adapter = null;
	protected TablesAdapter db = null;
	protected IndexTransaction tx = null;
	
	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this
				.withStep(indexFolder = StateWorkStep.of("Scan Folder", this::doFolder))
				.withStep(transaction = StateWorkStep.of("Commit Tx", this::doTx))
				.withStep(checkFileIndex = StateWorkStep.of("Check File Index", this::doCheckFileIndex))
				.withStep(checkFeedIndex = StateWorkStep.of("Check Feed Index", this::doCheckFeedIndex))
				.withStep(done = StateWorkStep.of("Done", this::done));
		
		this.currentSite = OperationContext.getOrThrow().getSite();
		
		this.currentVault = this.currentSite.getFeedsVault();
		
		this.folders.addLast(this.currentVault.getFileStore().fileReference(CommonPath.from("/" + this.feed)));
		
		this.tx = IndexTransaction.of(this.currentVault);
	}
	
	public StateWorkStep doFolder(TaskContext trun) throws OperatingContextException {
		OperationContext.getOrThrow().touch();
		
		FileStoreFile folder = this.folders.pollFirst();
		
		if (folder == null)
			return StateWorkStep.NEXT;

		folder.getFolderListing(new OperationOutcome<List<FileStoreFile>>() {
			@Override
			public void callback(List<FileStoreFile> result) throws OperatingContextException {
				if (result != null) {
					for (FileStoreFile file : result) {
						System.out.println(" - " + file.isFolder() + " : " + file);
						
						if (file.isFolder()) {
							ReindexFeedWork.this.folders.addLast(file);
						}
						else {
							ReindexFeedWork.this.tx.withUpdate(file.getPathAsCommon());
						}
					}
				}
				
				trun.resume();	// try next folder
			}
		});
		
		return StateWorkStep.WAIT;
	}
	
	public StateWorkStep doTx(TaskContext trun) throws OperatingContextException {
		System.out.println("start tx commit");

		this.tx.commit();

		System.out.println("finish tx commit");

		return StateWorkStep.NEXT;
	}
	
	public StateWorkStep doCheckFileIndex(TaskContext trun) throws OperatingContextException {
		FileStore fs = this.currentVault.getFileStore();
		
		if (fs instanceof LocalStore) {
			LocalStore ls = (LocalStore) fs;
			
			this.adapter.traverseIndex(this.currentVault, CommonPath.from("/"), -1, null, new BasicFilter() {
				@Override
				public ExpressionResult check(FileIndexAdapter adapter, IVariableAware scope, Vault vault, CommonPath path, RecordStruct file) throws OperatingContextException {
					if ((file != null) && (path != null)) {
						if ("Present".equals(file.getFieldAsString("State"))) {
							Path fp = ls.resolvePath(path);  // uses /feed name/feed path

							if ((fp == null) || Files.notExists(fp)) {
								System.out.println("- step 2 delete - " + file.getFieldAsString("State") + " - " + path);

								adapter.deleteFile(currentVault, path, now, RecordStruct.record()
										.with("Source", "Scan")
										.with("Op", "Delete")
										.with("TimeStamp", now)
										.with("Node", ApplicationHub.getNodeId()));
							}
						}
					}
					
					return ExpressionResult.accepted();
				}
			});
		}
		
		return StateWorkStep.NEXT;
	}
	
	public StateWorkStep doCheckFeedIndex(TaskContext trun) throws OperatingContextException {
		FileStore fs = this.currentVault.getFileStore();
		
		if (fs instanceof LocalStore) {
			LocalStore ls = (LocalStore) fs;
			
			this.db.traverseRecords(trun, "dcmFeed", CurrentRecord.current()
					.withNested(new dcraft.db.proc.BasicFilter() {
						@Override
						public ExpressionResult check(TablesAdapter db, IVariableAware scope, String table, Object val) throws OperatingContextException {
							String path = Struct.objectToString(db.getStaticScalar(table, val.toString(),"dcmPath")) + ".html";
							boolean found = true;

							if (StringUtil.isNotEmpty(path)) {
								CommonPath cp = CommonPath.from(path);        // uses /site/feed name/feed path

								if (cp != null) {
									Path fp = ls.resolvePath(cp.subpath(1));

									if ((fp == null) || Files.notExists(fp)) {
										System.out.println("- step 3 delete - " + fp);
										found = false;
									}
								}
							}

							if (! found) {
								db.retireRecord(table, val.toString());
							}

							return ExpressionResult.accepted();
						}
					})
			);
		}
		
		return StateWorkStep.NEXT;
	}
	
	public StateWorkStep done(TaskContext trun) throws OperatingContextException {
		return StateWorkStep.NEXT;
	}
}
