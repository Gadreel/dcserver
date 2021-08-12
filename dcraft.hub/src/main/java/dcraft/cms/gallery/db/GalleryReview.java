package dcraft.cms.gallery.db;

import dcraft.cms.gallery.work.GalleryReviewWork;
import dcraft.core.db.tasklist.TaskListUtil;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.*;
import dcraft.util.StringUtil;

import java.util.*;

public class GalleryReview implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		TablesAdapter db = TablesAdapter.of(request);

		RecordStruct data = request.getDataAsRecord();

		TaskHub.submit(Task.ofSubContext()
						.withTitle("Review galleries: ")
						.withWork(
								ChainWork.chain()
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
			String reportid = this.params.getFieldAsString("StepId");

			// retire all previously reviewed galleries / folders

			List<String> stepkeys = db.getListKeys("dcTaskList", reportid, "dcStepTask");

			for (String stepkey : stepkeys)
				db.retireList("dcTaskList", reportid, "dcStepTask", stepkey);

			// collect review

			TaskHub.submit(Task.ofSubContext()
							.withTitle("Review galleries")
							.withWork(GalleryReviewWork.work()),
					new TaskObserver() {
						@Override
						public void callback(TaskContext task) {

							try {
								ListStruct reviewresults = Struct.objectToList(task.getResult());

								/*
									{
										Path: file / folder
										Messages: [
											{
												Level: nnnn
												Message: aaaaa
											}
										]
									}
								 */

								Map<String, RecordStruct> folders = new HashMap<>();

								if (reviewresults != null) {
									// get the folders indexed
									for (int i = 0; i < reviewresults.size(); i++) {
										RecordStruct review = reviewresults.getItemAsRecord(i);

										String path = review.getFieldAsString("Path");

										if (! path.endsWith(".v")) {
											review.with("Files", ListStruct.list());

											folders.put(path, review);
										}
									}

									// get the files in the folders
									for (int i = 0; i < reviewresults.size(); i++) {
										RecordStruct review = reviewresults.getItemAsRecord(i);

										String path = review.getFieldAsString("Path");

										if (path.endsWith(".v")) {
											//CommonPath cp = CommonPath.from(path);
											int spos = path.lastIndexOf('/');

											String folderpath = path.substring(0, spos);

											RecordStruct folder = folders.get(folderpath);

											if (folder == null) {
												Logger.error("Unable to find folder for: " + path);
											}
											else {
												folder.getFieldAsList("Files").with(review);
											}
										}
									}
								}

								List<String> folderpaths = new ArrayList<>(folders.keySet());
								Collections.sort(folderpaths);

								int cnt = 0;

								for (String folderpath : folderpaths) {
									RecordStruct gallery = folders.get(folderpath);

									// add Ident to each gallery

									String ident = StringUtil.leftPad("" + cnt, 4, "0");

									gallery.with("Ident", ident);

									cnt++;

									// store review

									String reviewid = TaskListUtil.addTaskListRecord(db, RecordStruct.record()
											.with("Title", gallery.getFieldAsString("Path"))
											.with("Description", "Gallery folder analysis for go live.")
									);

									db.updateList("dcTaskList", reviewid, "dcStore", "Review", gallery);

									db.updateList("dcTaskList", reportid, "dcStepTask", ident, reviewid);
								}

							}
							catch (OperatingContextException x) {
								Logger.error("Unable to store review results");
							}

							// return the report id
							taskctx.returnEmpty();
						}
					}
			);
		}
	}
}
