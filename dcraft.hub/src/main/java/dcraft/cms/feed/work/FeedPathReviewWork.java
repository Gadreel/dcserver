package dcraft.cms.feed.work;

import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStoreFile;
import dcraft.filevault.FileStoreVault;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcome;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.*;
import dcraft.web.ui.UIUtil;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class FeedPathReviewWork extends StateWork {
	static public FeedPathReviewWork work(CommonPath path) {
		FeedPathReviewWork work = new FeedPathReviewWork();
		work.path = path;

		return work;
	}

	protected CommonPath path = null;

	protected ListStruct reviewresults = ListStruct.list();
	protected RecordStruct reviewresult = RecordStruct.record();
	protected Deque<FileStoreFile> files = new ArrayDeque<>();

	protected StateWorkStep reviewFile = null;
	protected StateWorkStep done = null;

	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this
				.withStep(reviewFile = StateWorkStep.of("Check File Index", this::doFile))
				.withStep(done = StateWorkStep.of("Done", this::doDone));

		FileStoreVault currentVault = OperationContext.getOrThrow().getSite().getFeedsVault();
		
		this.files.addLast(currentVault.getFileStore().fileReference(this.path));
	}

	public StateWorkStep doFile(TaskContext trun) throws OperatingContextException {
		OperationContext.getOrThrow().touch();

		FileStoreFile file = this.files.pollFirst();

		if (file == null)
			return StateWorkStep.NEXT;

		FileStoreVault currentVault = OperationContext.getOrThrow().getSite().getFeedsVault();

		TaskHub.submit(
				UIUtil.mockWebRequestTask(OperationContext.getOrThrow().getSite(), "Feed file review")
						.withWork(FeedFileReviewWork.of(currentVault, file.getPathAsCommon())),
				new TaskObserver() {
					@Override
					public void callback(TaskContext task) {
						if (task.getResult() != null) {
							FeedPathReviewWork.this.reviewresult = Struct.objectToRecord(task.getResult())
									.with("Path", file.getPath()
							);
						}
						else {
							FeedPathReviewWork.this.reviewresult = RecordStruct.record()
									.with("Path", file.getPath())
									.with("Messages", ListStruct.list(
											RecordStruct.record()
												.with("Level", "Error")
												.with("Message", "Unable to review feed file")
									)
							);
						}

						FeedPathReviewWork.this.reviewresults.with(FeedPathReviewWork.this.reviewresult);

						trun.resume();	// try next folder
					}
				}
		);

		return StateWorkStep.WAIT;
	}

	public StateWorkStep doDone(TaskContext trun) throws OperatingContextException {
		trun.setResult(this.reviewresult);

		return StateWorkStep.NEXT;
	}
}
