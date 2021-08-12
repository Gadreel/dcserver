package dcraft.cms.feed.db;

import dcraft.cms.feed.work.FeedPathReviewWork;
import dcraft.cms.util.FeedUtil;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.*;

/*
	this is for an existing review only, may also need a function for single new review?
 */
public class FeedFileIndexAndReview implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		TablesAdapter db = TablesAdapter.of(request);

		RecordStruct data = request.getDataAsRecord();
		String reviewid = data.getFieldAsString("StepId");

		RecordStruct review = Struct.objectToRecord(db.getList("dcTaskList", reviewid, "dcStore", "Review"));

		CommonPath path = CommonPath.from(review.getFieldAsString("Path"));
		String ident = review.getFieldAsString("Ident");

		FeedUtil.reindexFeedFile(path);

		TaskHub.submit(Task.ofSubContext()
						.withTitle("Review feed file: " + path)
						.withWork(FeedPathReviewWork.work(path)),
				new TaskObserver() {
					@Override
					public void callback(TaskContext task) {
						try {
							RecordStruct page = Struct.objectToRecord(task.getResult());

							page.with("Ident", ident);

							db.updateList("dcTaskList", reviewid, "dcStore", "Review", page);
						}
						catch (OperatingContextException x) {
							Logger.error("Unable to store review results");
						}

						callback.returnEmpty();
					}
				}
		);
	}
}
