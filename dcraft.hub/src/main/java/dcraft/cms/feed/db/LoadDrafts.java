package dcraft.cms.feed.db;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.proc.filter.CurrentRecord;
import dcraft.db.proc.filter.Unique;
import dcraft.db.tables.TablesAdapter;
import dcraft.filestore.CommonPath;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.xml.XElement;

import java.util.List;

public class LoadDrafts implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		TablesAdapter db = TablesAdapter.ofNow(request);
		
		Unique collector = (Unique) Unique.unique().withNested(
				CurrentRecord.current().withNested(HistoryFilter.forDraft())
		);

		db.traverseIndexRange(OperationContext.getOrThrow(), "dcmFeedHistory", "dcmDraftPath", null,null, collector);
		
		List<XElement> feeddefs = ResourceHub.getResources().getConfig().getTagListDeep("Feeds/Definition");
		
		ListStruct results = ListStruct.list();
		
		for (Object oid: collector.getValues()) {
			String id = oid.toString();
			
			CommonPath path = CommonPath.from(Struct.objectToString(db.getStaticScalar("dcmFeedHistory", id, "dcmPath")));
			String feed = path.getName(1);
			XElement def = null;
			
			for (XElement adef : feeddefs) {
				if (feed.equals(adef.attr("Alias"))) {
					def = adef;
					break;
				}
			}
			
			if (def == null) {
				Logger.error("Missing feed definition for: " + feed);
				continue;
			}
			
			String suid = Struct.objectToString(db.getStaticScalar("dcmFeedHistory", id, "dcmStartedBy"));
			String luid = Struct.objectToString(db.getStaticScalar("dcmFeedHistory", id, "dcmModifiedBy"));

			results.with(RecordStruct.record()
					.with("LocalPath", "pages".equals(path.getName(1)) ? path.subpath(2) : path.subpath(1))
					.with("Feed", feed)
					.with("FeedName", def.attr("Title"))
					.with("Highlight", Struct.objectToBooleanOrFalse(def.attr("Highlight")))
					.with("StartEdit", db.getStaticScalar("dcmFeedHistory", id, "dcmStartedAt"))
					.with("StartBy", RecordStruct.record()
							.with("Id", suid)
							.with("FirstName", Struct.objectToString(db.getStaticScalar("dcUser", suid, "dcFirstName")))
							.with("LastName", Struct.objectToString(db.getStaticScalar("dcUser", suid, "dcLastName")))
					)
					.with("LastEdit", db.getStaticScalar("dcmFeedHistory", id, "dcmModifiedAt"))
					.with("LastBy", RecordStruct.record()
							.with("Id", luid)
							.with("FirstName", Struct.objectToString(db.getStaticScalar("dcUser", luid, "dcFirstName")))
							.with("LastName", Struct.objectToString(db.getStaticScalar("dcUser", luid, "dcLastName")))
					)
			);
		}
		
		callback.returnValue(results);
	}
}
