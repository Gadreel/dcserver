package dcraft.cms.feed.db;

import dcraft.cms.util.FeedUtil;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.BaseStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;

public class AddFeed implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		TablesAdapter db = TablesAdapter.of(request);
		
		RecordStruct data = request.getDataAsRecord();

		String feed = data.getFieldAsString("Feed");
		String path = data.getFieldAsString("Path");

		if (! path.endsWith(".html"))
			path += ".html";

		String fpath = path;

		CommonPath cpath = FeedUtilDb.toFolderPath(feed, path);

		RecordStruct params = RecordStruct.record()
				.with("Template", data.getFieldAsString("Template", "standard"));
		
		FeedUtil.addFeed(cpath.toString(), params, new OperationOutcomeStruct() {
			@Override
			public void callback(BaseStruct result) throws OperatingContextException {
				if (this.hasErrors()) {
					callback.returnEmpty();
					return;
				}
				
				// add meta data
				if (data.isNotFieldEmpty("Meta")) {
					FeedUtilDb.addHistory(request.getInterface(), db, feed, fpath, ListStruct.list(
							RecordStruct.record()
									.with("Command", "SaveMeta")
									.with("Params", RecordStruct.record()
											.with("SetFields", data.getFieldAsString("Meta"))
									)
							)
					);
				}
				
				// add tag data
				if (data.isNotFieldEmpty("Tags")) {
					FeedUtilDb.addHistory(request.getInterface(), db, feed, fpath, ListStruct.list(
							RecordStruct.record()
									.with("Command", "SaveTags")
									.with("Params", RecordStruct.record()
											.with("SetTags", data.getFieldAsString("Tags"))
									)
							)
					);
				}
				
				// add content parts
				if (data.isNotFieldEmpty("Commands")) {
					FeedUtilDb.addHistory(request.getInterface(), db, feed, fpath, data.getFieldAsList("Commands"));
				}
				
				FeedUtilDb.saveHistory(request.getInterface(), db, feed, fpath, null, data.getFieldAsBooleanOrFalse("Publish"), callback);
			}
		});
	}
}
