package dcraft.cms.store.db;

import dcraft.db.request.query.LoadRecordRequest;
import dcraft.db.request.query.SelectFields;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.request.update.UpdateRecordRequest;
import dcraft.db.tables.TablesAdapter;
import dcraft.filestore.CommonPath;
import dcraft.filestore.local.LocalStoreFile;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.service.ServiceHub;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.builder.BuilderStateException;
import dcraft.struct.builder.ICompositeBuilder;
import dcraft.task.Task;
import dcraft.task.TaskHub;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.xml.XElement;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;

public class Util {
	static public void updateOrderStatus(TablesAdapter db, String id) throws OperatingContextException {
		List<String> itemIds = db.getStaticListKeys("dcmOrder", id, "dcmItemEntryId");
		
		ZonedDateTime lastNotify = Struct.objectToDateTime(db.getStaticScalar("dcmOrder", id, "dcmLastCustomerNotice"));
		
		StringBuilder comment = new StringBuilder();
		
		ListStruct changedItems = ListStruct.list();
		
		for (String iid : itemIds) {
			ZonedDateTime updated = Struct.objectToDateTime(db.getStaticList("dcmOrder", id, "dcmItemUpdated", iid));
			
			if ((updated != null) && ((lastNotify == null) || (updated.compareTo(lastNotify) > 0))) {
				String prodid = Struct.objectToString(db.getStaticList("dcmOrder", id, "dcmItemProduct", iid));
				String prodttitle = Struct.objectToString(db.getStaticScalar("dcmProduct", prodid, "dcmTitle"));
				
				if (changedItems.size() > 0)
					comment.append(", ");
				
				comment.append(prodttitle);
				
				changedItems.with(iid);
			}
		}

		// nothing more to do
		if (changedItems.size() == 0)
			return;

		ZonedDateTime stamp = TimeUtil.now();

		int totship = 0;
		int totpu = 0;
		int totcomp = 0;
		int totcan = 0;
		
		for (String iid : itemIds) {
			String istatus = Struct.objectToString(db.getStaticList("dcmOrder", id, "dcmItemStatus", iid));
			
			if ("AwaitingShipment".equals(istatus))
				totship++;
			else if ("AwaitingPickup".equals(istatus))
				totpu++;
			else if ("Completed".equals(istatus))
				totcomp++;
			else if ("Canceled".equals(istatus))
				totcan++;
		}
		
		String status = "AwaitingFulfillment";
		
		if (itemIds.size() == totcan) {
			status = "Canceled";
		}
		else if (itemIds.size() == (totcan + totcomp)) {
			status = "Completed";
		}
		else if (totpu > 0) {
			status = "AwaitingPickup";
		}
		else if (totship > 0) {
			status = "AwaitingShipment";
		}
		else if (totcomp > 0) {
			status = "PartiallyCompleted";
		}
		
		db.updateStaticScalar("dcmOrder", id, "dcmStatus", status);
		
		boolean sendEmail = (totpu > 0) || (totcomp > 0);
		
		RecordStruct audit = RecordStruct.record()
				.with("Origin", "Store")
				.with("Stamp", stamp)
				.with("Internal", false)
				.with("Comment", "Items updated: " + comment + (sendEmail ? " - customer notified" : ""))
				.with("Status", status);
		
		db.updateStaticList("dcmOrder", id, "dcmAudit", TimeUtil.stampFmt.format(stamp), audit);

		if (sendEmail) {
			db.updateStaticScalar("dcmOrder", id, "dcmLastCustomerNotice", stamp);

			TaskHub.submit(Task.ofSubtask("Order placed trigger", "STORE")
					.withTopic("Batch")
					.withMaxTries(5)
					.withTimeout(10)        // TODO this should be graduated - 10 minutes moving up to 30 minutes if fails too many times
					.withParams(RecordStruct.record()
							.with("Id", id)
							.with("UpdatedItems", changedItems)
					)
					.withScript(CommonPath.from("/dcm/store/event-order-updated.dcs.xml")));
		}
		
	}
	
	static public String productImagePath(TablesAdapter db, String id, String missingoverride) throws OperatingContextException {
		return storeImagePath(db, "dcmProduct", id, missingoverride);
	}
	
	static public String categoryImagePath(TablesAdapter db, String id, String missingoverride) throws OperatingContextException {
		return storeImagePath(db, "dcmCategory", id, missingoverride);
	}
	
	static public String storeImagePath(TablesAdapter db, String table, String id, String missingoverride) throws OperatingContextException {
		String area = "dcmCategory".equals(table) ? "category" : "product";

		String image = Struct.objectToString(db.getStaticScalar(table, id, "dcmImage"));
		String alias = Struct.objectToString(db.getStaticScalar(table, id, "dcmAlias"));

		if (StringUtil.isNotEmpty(alias)) {
			if (StringUtil.isEmpty(image))
				image = "main";

			String imagePath = "/store/" + area + "/" + alias + "/" + image;

			// there should always be a "thumb" so check for it

			LocalStoreFile file = (LocalStoreFile) OperationContext.getOrThrow().getSite().getGalleriesVault()
					.getFileStore().fileReference(CommonPath.from(imagePath + ".v/thumb.jpg"));

			if (file.exists())
				return imagePath;
		}

		XElement sset = ApplicationHub.getCatalogSettings("CMS-Store");

		if (sset != null) {
			image = StringUtil.isNotEmpty(missingoverride) ? missingoverride : sset.attr("MissingCategory");

			String imagePath = "/store/" + area + "/" + image;

			// there should always be a "thumb" so check for it

			LocalStoreFile file = (LocalStoreFile) OperationContext.getOrThrow().getSite().getGalleriesVault()
					.getFileStore().fileReference(CommonPath.from(imagePath + ".v/thumb.jpg"));

			if (file.exists())
				return imagePath;
		}

		return  "/store/" + area + "/not-found";
	}
}
