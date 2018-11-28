package dcraft.cms.feed.db;

import dcraft.cms.feed.work.CheckCmsReadyWork;
import dcraft.cms.feed.work.ReindexFeedWork;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.task.Task;
import dcraft.task.TaskContext;
import dcraft.task.TaskHub;
import dcraft.task.TaskObserver;

public class CmsCheck implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		String feed = request.getDataAsRecord().getFieldAsString("Feed");

		TaskHub.submit(Task.ofSubContext()
						.withTitle("CMS check feed: " + feed)
						.withWork(CheckCmsReadyWork.work(feed, request)),
				new TaskObserver() {
					@Override
					public void callback(TaskContext task) {
						callback.returnEmpty();
					}
				}
		);
	}
}
