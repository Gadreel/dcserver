package dcraft.cms.feed.db;

import dcraft.cms.feed.work.FeedFileReviewWork;
import dcraft.cms.feed.work.FeedReviewWork;
import dcraft.cms.feed.work.ReindexFeedWork;
import dcraft.cms.util.FeedUtil;
import dcraft.core.db.tasklist.TaskListUtil;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.*;
import dcraft.tenant.TenantHub;
import dcraft.util.RndUtil;
import dcraft.util.StringUtil;
import dcraft.web.ui.UIUtil;

import java.util.List;

public class FeedIndexAndReview implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		TablesAdapter db = TablesAdapter.ofNow(request);

		RecordStruct data = request.getDataAsRecord();
		String feed = data.getFieldAsString("Feed");

		TaskHub.submit(Task.ofSubContext()
						.withTitle("Reindex and review feed: " + feed)
						.withWork(
								ChainWork.chain()
									.then(ReindexFeedWork.work(feed, request, db))
									.then(new IWork() {
										@Override
										public void run(TaskContext taskctx) throws OperatingContextException {
											// ignore errors from reindex
											taskctx.clearExitCode();

											// make sure it is empty for steps below
											taskctx.returnEmpty();
										}
									})
									.then(FindCreateStep.work(data, request, db))
									.then(FillStep.work(data, db))
						),
				new TaskObserver() {
					@Override
					public void callback(TaskContext task) {
						callback.returnValue(task.getResult());
					}
				}
		);
	}

	static class FindCreateStep implements IWork {
		static public FindCreateStep work(RecordStruct data, ICallContext request, TablesAdapter db) {
			FindCreateStep work = new FindCreateStep();
			work.params = data;
			work.db = db;

			return work;
		}

		protected TablesAdapter db = null;
		protected RecordStruct params = null;

		@Override
		public void run(TaskContext taskctx) throws OperatingContextException {
			String feed = this.params.getFieldAsString("Feed");

			String trackerid = Struct.objectToString(db.getStaticList("dcTaskList", params.getFieldAsString("StepId"), "dcStepTask", "Tracker-" + feed));

			if (StringUtil.isNotEmpty(trackerid)) {
				taskctx.returnValue(RecordStruct.record()
						.with("Id", trackerid)
				);
				return;
			}

			RecordStruct def = FeedUtil.getFeedDefinition(feed);

			String reportid = TaskListUtil.addTaskListRecord(db, RecordStruct.record()
					.with("Title", def.getFieldAsString("Title") + " Feed Report")
					.with("Description", "Feed files analysis for go live.")
			);

			if (StringUtil.isNotEmpty(reportid)) {
				db.updateStaticList("dcTaskList", params.getFieldAsString("StepId"), "dcStepTask", "Tracker-" + feed, reportid);

				taskctx.returnValue(RecordStruct.record()
						.with("Id", reportid)
				);
			}
			else {
				taskctx.returnEmpty();
			}
		}
	}

	static class FillStep implements IWork {
		static public FillStep work(RecordStruct data, TablesAdapter db) {
			FillStep work = new FillStep();
			work.params = data;
			work.db = db;

			return work;
		}

		protected TablesAdapter db = null;
		protected RecordStruct params = null;

		@Override
		public void run(TaskContext taskctx) throws OperatingContextException {
			String feed = this.params.getFieldAsString("Feed");

			RecordStruct result = Struct.objectToRecord(taskctx.getResult());

			if (result == null) {
				taskctx.returnEmpty();
				return;
			}

			String reportid = result.getFieldAsString("Id");

			//RecordStruct def = FeedUtil.getFeedDefinition(feed);

			// retire all previously reviewed files

			List<String> stepkeys = db.getStaticListKeys("dcTaskList", reportid, "dcStepTask");

			for (String stepkey : stepkeys)
				db.retireStaticList("dcTaskList", reportid, "dcStepTask", stepkey);

			// collect review

			TaskHub.submit(Task.ofSubContext()
							.withTitle("Review feed: " + feed)
							.withWork(FeedReviewWork.work(feed)),
					new TaskObserver() {
						@Override
						public void callback(TaskContext task) {
							try {
								ListStruct reviewresults = Struct.objectToList(task.getResult());

								if (reviewresults != null) {
									for (int i = 0; i < reviewresults.size(); i++) {
										RecordStruct page = reviewresults.getItemAsRecord(i);

										// add Ident to each file

										String ident = RndUtil.nextUUId();

										page.with("Ident", ident);

										// store review

										String reviewid = TaskListUtil.addTaskListRecord(db, RecordStruct.record()
												.with("Title", page.getFieldAsString("Path"))
												.with("Description", "Feed file analysis for go live.")
										);

										db.updateStaticList("dcTaskList", reviewid, "dcStore", "Review", page);

										db.updateStaticList("dcTaskList", reportid, "dcStepTask", ident, reviewid);
									}
								}
							}
							catch (OperatingContextException x) {
								Logger.error("Unable to store review results");
							}

								/*
							try {
								db.updateStaticList("dcTaskList", reportid, "dcStore", "Review", reviewresults);
							}
							catch (OperatingContextException x) {
								Logger.error("Unable to store review results");
							}
								 */

							// return the report id
							taskctx.returnValue(result);
						}
					}
			);
		}
	}
}
