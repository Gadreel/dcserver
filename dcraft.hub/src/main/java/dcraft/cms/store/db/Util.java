package dcraft.cms.store.db;

import dcraft.db.proc.BasicFilter;
import dcraft.db.proc.ExpressionResult;
import dcraft.db.proc.filter.ActiveConfirmed;
import dcraft.db.proc.filter.CurrentRecord;
import dcraft.db.proc.filter.Unique;
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
import dcraft.task.IWork;
import dcraft.task.Task;
import dcraft.task.TaskContext;
import dcraft.task.TaskHub;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.xml.XElement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
	
	static public String productImagePath(TablesAdapter db, String id, String variant, String missingoverride) throws OperatingContextException {
		return storeImagePath(db, "dcmProduct", id, variant, missingoverride);
	}
	
	static public String categoryImagePath(TablesAdapter db, String id, String variant, String missingoverride) throws OperatingContextException {
		return storeImagePath(db, "dcmCategory", id, variant, missingoverride);
	}
	
	static public String storeImagePath(TablesAdapter db, String table, String id, String variant, String missingoverride) throws OperatingContextException {
		String area = "dcmCategory".equals(table) ? "category" : "product";

		if (StringUtil.isEmpty(variant))
			variant = "thumb";

		String image = Struct.objectToString(db.getStaticScalar(table, id, "dcmImage"));
		String alias = Struct.objectToString(db.getStaticScalar(table, id, "dcmAlias"));

		if (StringUtil.isNotEmpty(alias)) {
			if (StringUtil.isEmpty(image))
				image = "main";

			String imagePath = "/store/" + area + "/" + alias + "/" + image;

			// there should always be a "thumb" so check for it

			LocalStoreFile file = (LocalStoreFile) OperationContext.getOrThrow().getSite().getGalleriesVault()
					.getFileStore().fileReference(CommonPath.from(imagePath + ".v/" + variant + ".jpg"));

			if (file.exists())
				return imagePath;
		}

		XElement sset = ApplicationHub.getCatalogSettings("CMS-Store");

		if (sset != null) {
			image = StringUtil.isNotEmpty(missingoverride) ? missingoverride : sset.attr("MissingCategory");

			String imagePath = "/store/" + area + "/" + image;

			// there should always be a "thumb" so check for it

			LocalStoreFile file = (LocalStoreFile) OperationContext.getOrThrow().getSite().getGalleriesVault()
					.getFileStore().fileReference(CommonPath.from(imagePath + ".v/" + variant + ".jpg"));

			if (file.exists())
				return imagePath;
		}

		return  "/store/" + area + "/not-found";
	}

						/*
	static public String findDiscountRuleForProduct(TablesAdapter db, String productid) throws OperatingContextException {
		Unique collector = Unique.unique();
		ZonedDateTime now = ZonedDateTime.now();

		collector.withNested(CurrentRecord.current().withNested(
				new BasicFilter() {
					@Override
					public ExpressionResult check(TablesAdapter adapter, IVariableAware scope, String table, Object val) throws OperatingContextException {
						String did = val.toString();

						if (Struct.objectToBoolean(adapter.getStaticScalar(table, did, "dcmActive"), false)) {
							ZonedDateTime start = Struct.objectToDateTime(adapter.getStaticScalar(table, did, "dcmStart"));
							ZonedDateTime end = Struct.objectToDateTime(adapter.getStaticScalar(table, did, "dcmExpire"));

							if ((start == null) || start.isBefore(now)) {
								if ((end == null) || end.isAfter(now)) {
									if (db.getStaticList("dcmDiscount", did, "dcmRuleProduct", productid) != null)
										return ExpressionResult.FOUND;
								}
							}
						}

			<!-- Products group - for rules - FixedOffProduct,PercentOffProduct only -->
			<Field Name="dcmRuleProduct" Group="Product" Type="Id" List="True" />
			<Field Name="dcmRuleMode" Group="Product" Type="dcmDiscountModeEnum" List="True" />
			<Field Name="dcmRuleAmount" Group="Product" Type="Decimal" List="True" />

						return ExpressionResult.REJECTED;
					}
				})
		);

		db.traverseIndexReverseRange(OperationContext.getOrNull(), "dcmDiscount", "dcmType", "Rule", "Rule", collector);

		if (collector.getValues().size() > 0)
			return collector.getOne().toString();

		return null;
	}
						 */

	static public void resolveDiscountRules(TablesAdapter db) throws OperatingContextException {
		ZonedDateTime now = ZonedDateTime.now();

		CurrentRecord collector = CurrentRecord.current();

		Set<String> prodstouched = new HashSet<>();

		collector.withNested(
			new BasicFilter() {
				@Override
				public ExpressionResult check(TablesAdapter adapter, IVariableAware scope, String table, Object val) throws OperatingContextException {
					String did = val.toString();

					boolean activediscount = Struct.objectToBoolean(adapter.getStaticScalar(table, did, "dcmActive"), false);

					ZonedDateTime start = Struct.objectToDateTime(adapter.getStaticScalar(table, did, "dcmStart"));
					ZonedDateTime end = Struct.objectToDateTime(adapter.getStaticScalar(table, did, "dcmExpire"));

					boolean activerule = false;

					if (activediscount) {
						if ((start == null) || start.isBefore(now)) {
							if ((end == null) || end.isAfter(now)) {
								activerule = true;
							}
						}
					}

					if ((end != null) && end.isBefore(now)) {
						adapter.updateStaticScalar("dcmDiscount", did, "dcmActive", false);
						activediscount = false;
					}

					List<String> prods = adapter.getStaticListKeys("dcmDiscount", did, "dcmRuleProduct");

					for (String pid : prods) {
						BigDecimal oldsaleprice = Struct.objectToDecimal(adapter.getStaticScalar("dcmProduct", pid, "dcmSalePrice"));

						if (activerule) {
							BigDecimal saleprice = Struct.objectToDecimal(adapter.getStaticScalar("dcmProduct", pid, "dcmPrice"));

							if (saleprice == null)
								saleprice = BigDecimal.ZERO;

							String mode = Struct.objectToString(adapter.getStaticList("dcmDiscount", did, "dcmRuleMode", pid));
							BigDecimal amount = Struct.objectToDecimal(adapter.getStaticList("dcmDiscount", did, "dcmRuleAmount", pid));

							if ("FixedOffProduct".equals(mode)) {
								saleprice = amount;
							}
							else {
								saleprice = saleprice.subtract(saleprice.multiply(amount).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
							}

							if ((oldsaleprice == null) || saleprice.compareTo(oldsaleprice) != 0) {
								adapter.updateStaticScalar("dcmProduct", pid, "dcmSalePrice", saleprice);

								Logger.info("Product sale started: " + pid + " at " + saleprice.toPlainString());
							}
						}
						else if (! prodstouched.contains(pid)) {
							if (oldsaleprice != null) {
								adapter.retireStaticScalar("dcmProduct", pid, "dcmSalePrice");

								Logger.info("Product sale ended: " + pid);
							}
						}

						prodstouched.add(pid);
					}

					if (! activediscount)
						adapter.updateStaticScalar("dcmDiscount", did,"dcmState",  "Ignore");

					return ExpressionResult.ACCEPTED;
				}
			}
		);

		// forward direction so that the latest rules override
		db.traverseIndex(OperationContext.getOrNull(), "dcmDiscount", "dcmState", "Check", collector);
	}

	static public void scheduleDiscountRules(TablesAdapter db) throws OperatingContextException {
		ZonedDateTime recent = ZonedDateTime.now().minusMinutes(1);
		ZonedDateTime soon = ZonedDateTime.now().plusMinutes(15);

		CurrentRecord collector = CurrentRecord.current();

		collector.withNested(
			new BasicFilter() {
				@Override
				public ExpressionResult check(TablesAdapter adapter, IVariableAware scope, String table, Object val) throws OperatingContextException {
					String did = val.toString();

					boolean activediscount = Struct.objectToBoolean(adapter.getStaticScalar(table, did, "dcmActive"), false);

					if (activediscount) {
						ZonedDateTime start = Struct.objectToDateTime(adapter.getStaticScalar(table, did, "dcmStart"));
						ZonedDateTime end = Struct.objectToDateTime(adapter.getStaticScalar(table, did, "dcmExpire"));

						if ((start != null) && start.isBefore(soon) && start.isAfter(recent)) {
							TaskHub.scheduleAt(Task.ofSubContext()
											.withTitle("Store Discount Scheduled")
											.withWork(new IWork() {
												@Override
												public void run(TaskContext taskctx) throws OperatingContextException {
													Util.resolveDiscountRules(db);
													taskctx.returnEmpty();
												}
											}),
									start);
						}

						if ((end != null) && end.isBefore(soon) && end.isAfter(recent)) {
							TaskHub.scheduleAt(Task.ofSubContext()
											.withTitle("Store Discount Scheduled")
											.withWork(new IWork() {
												@Override
												public void run(TaskContext taskctx) throws OperatingContextException {
													Util.resolveDiscountRules(db);
													taskctx.returnEmpty();
												}
											}),
									end);
						}
					}

					return ExpressionResult.ACCEPTED;
				}
			}
		);

		// forward direction so that the latest rules override
		db.traverseIndex(OperationContext.getOrNull(), "dcmDiscount", "dcmState", "Check", collector);
	}
}
