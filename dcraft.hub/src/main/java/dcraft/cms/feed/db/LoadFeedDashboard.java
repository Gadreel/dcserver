package dcraft.cms.feed.db;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.proc.filter.CurrentRecord;
import dcraft.db.proc.filter.Unique;
import dcraft.db.request.query.CollectorField;
import dcraft.db.request.query.SelectDirectRequest;
import dcraft.db.request.query.SelectFields;
import dcraft.db.tables.TablesAdapter;
import dcraft.filestore.CommonPath;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.util.List;

public class LoadFeedDashboard implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		TablesAdapter db = TablesAdapter.ofNow(request);

		List<XElement> feeddefs = ResourceHub.getResources().getConfig().getTagListDeep("Feeds/Definition");
		
		ListStruct results = ListStruct.list();

		for (XElement def : feeddefs) {
			String feed = def.attr("Alias");

			if (! Struct.objectToBooleanOrFalse(def.attr("Highlight")))
				continue;

			Unique collector = (Unique) Unique.unique().withNested(CurrentRecord.current());

			Scan scan = new Scan();

			CollectorField collectorField = CollectorField.collect()
					.withFunc("dcmScanFeed")
					.withFrom("+")
					.withTo("-")
					.withExtras(RecordStruct.record()
							.with("Feed", feed)
					)
					.withMax(1);

			scan.collect(request, db, OperationContext.getOrThrow(), "dcmFeed",
					(RecordStruct) collectorField.getParams(), collector);

			String id = (String) collector.getOne();

			if (StringUtil.isNotEmpty(id)) {
				String mylocale = "." + OperationContext.getOrThrow().getResources().getLocale().getDefaultLocale();
				String deflocale = "." + OperationContext.getOrThrow().getTenant().getResources().getLocale().getDefaultLocale();

				String title = Struct.objectToString(db.getStaticList("dcmFeed", id, "dcmLocaleFields", "Title" + mylocale));

				if (StringUtil.isEmpty(title))
					title = Struct.objectToString(db.getStaticList("dcmFeed", id, "dcmLocaleFields", "Title" + deflocale));

				results.with(RecordStruct.record()
						.with("LocalPath", db.getStaticScalar("dcmFeed", id, "dcmLocalPath"))
						.with("Feed", feed)
						.with("FeedName", def.attr("Title"))
						.with("Title", title)
						.with("Published", db.getStaticScalar("dcmFeed", id, "dcmPublishAt"))
				);
			}
			else {
				results.with(RecordStruct.record()
						.with("Feed", feed)
						.with("FeedName", def.attr("Title"))
				);
			}
		}

		callback.returnValue(results);
	}
}
