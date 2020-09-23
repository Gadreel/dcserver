package dcraft.cms.feed.work;

import dcraft.db.ICallContext;
import dcraft.db.fileindex.BasicFilter;
import dcraft.db.fileindex.FileIndexAdapter;
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
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcome;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.*;
import dcraft.tenant.Site;
import dcraft.tenant.TenantHub;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.web.ui.UIUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class FeedReviewWork extends FeedPathReviewWork {
	static public FeedReviewWork work(String feed) {
		FeedReviewWork work = new FeedReviewWork();
		work.feed = feed;

		return work;
	}

	protected String feed = null;

	protected Deque<FileStoreFile> folders = new ArrayDeque<>();

	protected StateWorkStep scanFolder = null;

	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this
				.withStep(scanFolder = StateWorkStep.of("Scan Folder", this::doFolder))
				.withStep(reviewFile = StateWorkStep.of("Check File Index", this::doFile))
				.withStep(done = StateWorkStep.of("Done", this::doDone));

		FileStoreVault currentVault = OperationContext.getOrThrow().getSite().getFeedsVault();
		
		this.folders.addLast(currentVault.getFileStore().fileReference(CommonPath.from("/" + this.feed)));
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
						//System.out.println(" - " + file.isFolder() + " : " + file);
						
						if (file.isFolder())
							FeedReviewWork.this.folders.addLast(file);
						else
							FeedReviewWork.this.files.addLast(file);
					}
				}
				
				trun.resume();	// try next folder
			}
		});
		
		return StateWorkStep.WAIT;
	}

	public StateWorkStep doDone(TaskContext trun) throws OperatingContextException {
		trun.setResult(this.reviewresults);

		return StateWorkStep.NEXT;
	}
}
