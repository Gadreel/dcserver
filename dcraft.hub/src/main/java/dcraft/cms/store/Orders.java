package dcraft.cms.store;

import dcraft.db.request.query.CollectorField;
import dcraft.db.request.query.LoadRecordRequest;
import dcraft.db.request.query.SelectDirectRequest;
import dcraft.db.request.query.SelectFields;
import dcraft.db.request.query.WhereAnd;
import dcraft.db.request.query.WhereContains;
import dcraft.db.request.query.WhereField;
import dcraft.db.request.query.WhereOr;
import dcraft.db.request.query.WhereStartsWith;
import dcraft.db.request.query.WhereTerm;
import dcraft.db.request.query.WhereUtil;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.request.update.UpdateRecordRequest;
import dcraft.filestore.CommonPath;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.service.ServiceHub;
import dcraft.service.ServiceRequest;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.Task;
import dcraft.task.TaskHub;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;

import java.time.ZonedDateTime;

public class Orders {
	
	/******************************************************************
	 * Orders
	 ******************************************************************/
	static public boolean handle(ServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException {
		String op = request.getOp();
		
		RecordStruct rec = request.getDataAsRecord();
		
		if ("Calculate".equals(op)) {
			OrderUtil.santitizeAndCalculateOrder(rec, callback);
			return true;
		}
		else if ("Submit".equals(op)) {
			OrderUtil.processAuthOrder(rec, callback);
			return true;
		}
		else if ("ListMy".equals(op)) {
			Orders.handleListMy(request, callback);
			return true;
		}
		else if ("LoadMy".equals(op)) {
			Orders.handleLoadMy(request, callback);
			return true;
		}
		else if ("Load".equals(op)) {
			Orders.handleLoad(request, callback);
			return true;
		}
		else if ("UpdateStatus".equals(op)) {
			Orders.handleUpdateStatus(request, callback);
			return true;
		}
		else if ("UpdateItems".equals(op)) {
			Orders.handleUpdateItems(request, callback);
			return true;
		}
		else if ("AddComment".equals(op)) {
			Orders.handleAddComment(request, callback);
			return true;
		}
		else if ("Search".equals(op)) {
			Orders.handleSearch(request, callback);
			return true;
		}
		
		return false;
	}

	static public void handleListMy(ServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		SelectDirectRequest req = new SelectDirectRequest()
				.withTable("dcmOrder")
				.withSelect(new SelectFields()
						.with("Id")
						.with("dcmOrderDate", "OrderDate")
						.with("dcmStatus", "Status")
						.with("dcmCustomerInfo", "CustomerInfo")
						.with("dcmDelivery", "Delivery")
						.with("dcmGrandTotal", "GrandTotal")
				).withCollector(CollectorField.collect()
					.withField("dcmCustomer")
					.withValues(OperationContext.getOrThrow().getUserContext().getUserId())
				);

		ServiceHub.call(req.toServiceRequest().withOutcome(callback));
	}

	static public void handleLoadMy(ServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		LoadRecordRequest req = LoadRecordRequest.of("dcmOrder")
				.withId(data.getFieldAsString("Id"))
				.withSelect(new SelectFields()
						.with("dcmOrderDate", "OrderDate")
						.with("dcmStatus", "Status")
						.with("dcmLastStatusDate", "LastStatusDate")
						.with("dcmCustomer", "Customer")
						.with("dcmCustomerInfo", "CustomerInfo")
						.with("dcmDelivery", "Delivery")
						.withSubquery("dcmItemProduct", "ProductInfo", SelectFields.select()
								.with("Id", "Product")
								.with("dcmTitle", "Title")
								.with("dcmAlias", "Alias")
								.with("dcmSku", "Sku")
								.with("dcmDescription", "Description")
								.with("dcmInstructions", "Instructions")
								.with("dcmImage", "Image")
						)
						.with("dcmItemProduct", "ItemProduct", null, true)
						.with("dcmItemQuantity", "ItemQuantity", null,true)
						.with("dcmItemPrice", "ItemPrice", null, true)
						.with("dcmItemTotal", "ItemTotal", null,true)
						.with("dcmItemStatus", "ItemStatus", null,true)
						.with("dcmShippingInfo", "ShippingInfo")
						.with("dcmBillingInfo", "BillingInfo")
						.with("dcmComment", "Comment")
						.with("dcmCouponCodes", "CouponCodes")
						.with("dcmDiscounts", "Discounts")
						.with("dcmPaymentId", "PaymentId")
						.with("dcmPaymentInfo", "PaymentInfo")
						.with("dcmCalcInfo", "CalcInfo")
						.with("dcmGrandTotal", "GrandTotal")
				);

		ServiceHub.call(req.toServiceRequest().withOutcome(new OperationOutcomeStruct() {
			@Override
			public void callback(Struct result) throws OperatingContextException {
				if (! this.hasErrors() && this.isNotEmptyResult()) {
					RecordStruct rec = Struct.objectToRecord(result);

					if (OperationContext.getOrThrow().getUserContext().getUserId().equals(rec.getFieldAsString("Customer"))) {
						ListStruct finallist = ListStruct.list();

						ListStruct pinfo = rec.getFieldAsList("ProductInfo");
						rec.removeField("ProductInfo");

						ListStruct plist = rec.getFieldAsList("ItemProduct");
						rec.removeField("ItemProduct");

						ListStruct qlist = rec.getFieldAsList("ItemQuantity");
						rec.removeField("ItemQuantity");

						ListStruct alist = rec.getFieldAsList("ItemPrice");
						rec.removeField("ItemPrice");

						ListStruct tlist = rec.getFieldAsList("ItemTotal");
						rec.removeField("ItemTotal");

						ListStruct slist = rec.getFieldAsList("ItemStatus");
						rec.removeField("ItemStatus");

						for (Struct pentry : plist.items()) {
							RecordStruct prec = Struct.objectToRecord(pentry);

							String eid = prec.getFieldAsString("SubId");
							String pid = prec.getFieldAsString("Data");

							RecordStruct resrec = RecordStruct.record()
									.with("EntryId", eid);

							for (Struct prodentry : pinfo.items()) {
								RecordStruct prodrec = Struct.objectToRecord(prodentry);

								if (pid.equals(prodrec.getFieldAsString("Product"))) {
									resrec.copyFields(prodrec);
									break;
								}
							}

							for (Struct xentry : qlist.items()) {
								RecordStruct xrec = Struct.objectToRecord(xentry);

								if (eid.equals(xrec.getFieldAsString("SubId"))) {
									resrec.with("Quantity", xrec.getField("Data"));
									break;
								}
							}

							for (Struct xentry : alist.items()) {
								RecordStruct xrec = Struct.objectToRecord(xentry);

								if (eid.equals(xrec.getFieldAsString("SubId"))) {
									resrec.with("Price", xrec.getField("Data"));
									break;
								}
							}

							for (Struct xentry : tlist.items()) {
								RecordStruct xrec = Struct.objectToRecord(xentry);

								if (eid.equals(xrec.getFieldAsString("SubId"))) {
									resrec.with("Total", xrec.getField("Data"));
									break;
								}
							}

							for (Struct xentry : slist.items()) {
								RecordStruct xrec = Struct.objectToRecord(xentry);

								if (eid.equals(xrec.getFieldAsString("SubId"))) {
									resrec.with("Status", xrec.getField("Data"));
									break;
								}
							}

							finallist.with(resrec);
						}

						rec.with("Items", finallist);

						callback.returnValue(rec);
						return;
					}

					Logger.error("You do not have access to this order.");
				}

				callback.returnEmpty();
			}
		}));
	}

	static public void handleLoad(ServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();
		
		LoadRecordRequest req = LoadRecordRequest.of("dcmOrder")
				.withId(data.getFieldAsString("Id"))
				.withSelect(SelectFields.select()
						.with("dcmOrderDate", "OrderDate")
						.with("dcmStatus", "Status")
						.with("dcmLastStatusDate", "LastStatusDate")
						.with("dcmCustomer", "Customer")
						.with("dcmCustomerInfo", "CustomerInfo")
						.with("dcmDelivery", "Delivery")
						.withSubquery("dcmItemProduct", "ProductInfo", SelectFields.select()
								.with("Id", "Product")
								.with("dcmTitle", "Title")
								.with("dcmAlias", "Alias")
								.with("dcmSku", "Sku")
								.with("dcmDescription", "Description")
								.with("dcmInstructions", "Instructions")
								.with("dcmImage", "Image")
						)
						.with("dcmItemProduct", "ItemProduct", null, true)
						.with("dcmItemQuantity", "ItemQuantity", null,true)
						.with("dcmItemPrice", "ItemPrice", null, true)
						.with("dcmItemTotal", "ItemTotal", null,true)
						.with("dcmItemStatus", "ItemStatus", null,true)
						.with("dcmShippingInfo", "ShippingInfo")
						.with("dcmBillingInfo", "BillingInfo")
						.with("dcmComment", "Comment")
						.with("dcmCouponCodes", "CouponCodes")
						.with("dcmDiscounts", "Discounts")
						.with("dcmPaymentId", "PaymentId")
						.with("dcmPaymentInfo", "PaymentInfo")
						.with("dcmCalcInfo", "CalcInfo")
						.with("dcmGrandTotal", "GrandTotal")
						.with("dcmAudit", "Audit")
				);

		// Alias, Image, Instructions, Title - Price, Quantity, Total

		ServiceHub.call(req.toServiceRequest().withOutcome(new OperationOutcomeStruct() {
			@Override
			public void callback(Struct result) throws OperatingContextException {
				if (this.hasErrors()) {
					callback.returnEmpty();
					return;
				}

				RecordStruct rec = Struct.objectToRecord(result);
				ListStruct finallist = ListStruct.list();

				ListStruct pinfo = rec.getFieldAsList("ProductInfo");
				rec.removeField("ProductInfo");

				ListStruct plist = rec.getFieldAsList("ItemProduct");
				rec.removeField("ItemProduct");

				ListStruct qlist = rec.getFieldAsList("ItemQuantity");
				rec.removeField("ItemQuantity");

				ListStruct alist = rec.getFieldAsList("ItemPrice");
				rec.removeField("ItemPrice");

				ListStruct tlist = rec.getFieldAsList("ItemTotal");
				rec.removeField("ItemTotal");

				ListStruct slist = rec.getFieldAsList("ItemStatus");
				rec.removeField("ItemStatus");

				for (Struct pentry : plist.items()) {
					RecordStruct prec = Struct.objectToRecord(pentry);

					String eid = prec.getFieldAsString("SubId");
					String pid = prec.getFieldAsString("Data");

					RecordStruct resrec = RecordStruct.record()
							.with("EntryId", eid);

					for (Struct prodentry : pinfo.items()) {
						RecordStruct prodrec = Struct.objectToRecord(prodentry);

						if (pid.equals(prodrec.getFieldAsString("Product"))) {
							resrec.copyFields(prodrec);
							break;
						}
					}

					for (Struct xentry : qlist.items()) {
						RecordStruct xrec = Struct.objectToRecord(xentry);

						if (eid.equals(xrec.getFieldAsString("SubId"))) {
							resrec.with("Quantity", xrec.getField("Data"));
							break;
						}
					}

					for (Struct xentry : alist.items()) {
						RecordStruct xrec = Struct.objectToRecord(xentry);

						if (eid.equals(xrec.getFieldAsString("SubId"))) {
							resrec.with("Price", xrec.getField("Data"));
							break;
						}
					}

					for (Struct xentry : tlist.items()) {
						RecordStruct xrec = Struct.objectToRecord(xentry);

						if (eid.equals(xrec.getFieldAsString("SubId"))) {
							resrec.with("Total", xrec.getField("Data"));
							break;
						}
					}

					for (Struct xentry : slist.items()) {
						RecordStruct xrec = Struct.objectToRecord(xentry);

						if (eid.equals(xrec.getFieldAsString("SubId"))) {
							resrec.with("Status", xrec.getField("Data"));
							break;
						}
					}

					finallist.with(resrec);
				}

				rec.with("Items", finallist);

				callback.returnValue(rec);
			}
		}));
	}

	static public void handleUpdateStatus(ServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();
		String id = data.getFieldAsString("Id");
		String status = data.getFieldAsString("Status");

		ZonedDateTime stamp = TimeUtil.now();

		RecordStruct audit = RecordStruct.record()
				.with("Origin", "Store")
				.with("Stamp", stamp)
				.with("Internal", false)
				.with("Comment", "Status updated")
				.with("Status", status);

		DbRecordRequest upreq = UpdateRecordRequest.update()
				.withTable("dcmOrder")
				.withId(id)
				.withSetField("dcmAudit", TimeUtil.stampFmt.format(stamp), audit)
				.withUpdateField("dcmStatus", status);

		if ("Completed".equals(status)) {
			// complete all items as well
			LoadRecordRequest req = LoadRecordRequest.of("dcmOrder")
					.withId(id)
					.withSelect(SelectFields.select()
							.with("dcmItemStatus", "ItemStatus", null,true)
					);

			ServiceHub.call(req.toServiceRequest().withOutcome(new OperationOutcomeStruct() {
				@Override
				public void callback(Struct result) throws OperatingContextException {
					if (this.hasErrors()) {
						callback.returnEmpty();
						return;
					}

					RecordStruct rec = Struct.objectToRecord(result);

					for (Struct xentry : rec.getFieldAsList("ItemStatus").items()) {
						RecordStruct xrec = Struct.objectToRecord(xentry);

						upreq
								.withUpdateField("dcmItemStatus", xrec.getFieldAsString("SubId"), "Completed");
					}
					
					audit.with("Comment", "Order status updated - customer notified");

					ServiceHub.call(upreq
							.toServiceRequest()
							.withOutcome(new OperationOutcomeStruct() {
								@Override
								public void callback(Struct upresult) throws OperatingContextException {
									TaskHub.submit(Task.ofSubtask("Order placed trigger", "STORE")
											.withTopic("Batch")
											.withMaxTries(5)
											.withTimeout(10)		// TODO this should be graduated - 10 minutes moving up to 30 minutes if fails too many times
											.withParams(RecordStruct.record()
													.with("Id", id)
											)
											.withScript(CommonPath.from("/dcm/store/event-order-placed.dcs.xml")));
									
									callback.returnValue(upresult);
								}
							})
					);
				}
			}));
		}
		else {
			ServiceHub.call(upreq
					.toServiceRequest()
					.withOutcome(callback)
			);
		}
	}

	static public void handleUpdateItems(ServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();
		String id = data.getFieldAsString("Id");
		String status = data.getFieldAsString("Status");
		ListStruct updateList = data.getFieldAsList("Items");

		LoadRecordRequest req = LoadRecordRequest.of("dcmOrder")
				.withId(id)
				.withSelect(SelectFields.select()
						.withSubquery("dcmItemProduct", "ProductInfo", SelectFields.select()
								.with("Id", "Product")
								.with("dcmTitle", "Title")
						)
						.with("dcmItemProduct", "ItemProduct", null, true)
						.with("dcmItemStatus", "ItemStatus", null,true)
				);

		ServiceHub.call(req.toServiceRequest().withOutcome(new OperationOutcomeStruct() {
			@Override
			public void callback(Struct result) throws OperatingContextException {
				if (this.hasErrors()) {
					callback.returnEmpty();
					return;
				}

				RecordStruct rec = Struct.objectToRecord(result);
				StringBuilder comment = new StringBuilder();

				DbRecordRequest upreq = UpdateRecordRequest.update()
						.withTable("dcmOrder")
						.withId(id);

				int fndcnt = 0;

				ListStruct pinfo = rec.getFieldAsList("ProductInfo");
				ListStruct plist = rec.getFieldAsList("ItemProduct");
				ListStruct slist = rec.getFieldAsList("ItemStatus");

				for (Struct pentry : plist.items()) {
					RecordStruct prec = Struct.objectToRecord(pentry);

					String eid = prec.getFieldAsString("SubId");
					String pid = prec.getFieldAsString("Data");

					boolean fnd = false;

					for (Struct upentry : updateList.items()) {
						String upid = Struct.objectToString(upentry);

						if (upid.equals(eid)) {
							fnd = true;
							break;
						}
					}

					if (! fnd)
						continue;

					upreq
							.withUpdateField("dcmItemStatus", eid, status);

					for (Struct prodentry : pinfo.items()) {
						RecordStruct prodrec = Struct.objectToRecord(prodentry);

						if (pid.equals(prodrec.getFieldAsString("Product"))) {
							if (fndcnt > 0)
								comment.append(", ");

							comment.append(prodrec.getFieldAsString("Title"));
							fndcnt++;
							break;
						}
					}
				}

				if (fndcnt > 0) {
					int totitm = plist.size();
					int totfull = 0;
					int totcomp = 0;
					int totcan = 0;
					boolean sendEmail = false;
					
					for (Struct sentry : slist.items()) {
						RecordStruct srec = Struct.objectToRecord(sentry);
						
						String eid = srec.getFieldAsString("SubId");
						String sstatus = srec.getFieldAsString("Data");
						
						boolean fnd = false;
						
						for (Struct upentry : updateList.items()) {
							String upid = Struct.objectToString(upentry);
							
							if (upid.equals(eid)) {
								fnd = true;
								break;
							}
						}
						
						if (fnd)
							continue;
						
						if ("AwaitingFulfillment".equals(sstatus))
							totfull++;
						else if ("Completed".equals(sstatus))
							totcomp++;
						else if ("Canceled".equals(sstatus))
							totcan++;
					}
					
					// update overall order status when appropriate
					if ("AwaitingShipment".equals(status)) {
						upreq
								.withUpdateField("dcmStatus", status);
					}
					else if ("AwaitingPickup".equals(status)) {
						upreq
								.withUpdateField("dcmStatus", status);
						
						sendEmail = true;
					}
					else if ("AwaitingFulfillment".equals(status)) {
						totfull += updateList.size();
						
						if (totfull == totitm)
							upreq
								.withUpdateField("dcmStatus", status);
					}
					else if ("Completed".equals(status)) {
						totcomp += updateList.size();
						
						if (totcomp == totitm)
							upreq
								.withUpdateField("dcmStatus", status);
						
						sendEmail = true;
					}
					else if ("Canceled".equals(status)) {
						totcan += updateList.size();
						
						if (totcan == totitm)
							upreq
								.withUpdateField("dcmStatus", status);
					}
					
					ZonedDateTime stamp = TimeUtil.now();

					RecordStruct audit = RecordStruct.record()
							.with("Origin", "Store")
							.with("Stamp", stamp)
							.with("Internal", false)
							.with("Comment", "Items updated: " + comment + (sendEmail ? " - customer notified" : ""))
							.with("Status", status);

					upreq
						.withSetField("dcmAudit", TimeUtil.stampFmt.format(stamp), audit);

					boolean sendEmailF = sendEmail;
					
					ServiceHub.call(upreq
							.toServiceRequest()
							.withOutcome(new OperationOutcomeStruct() {
								@Override
								public void callback(Struct upresult) throws OperatingContextException {
									if (sendEmailF) {
										TaskHub.submit(Task.ofSubtask("Order placed trigger", "STORE")
												.withTopic("Batch")
												.withMaxTries(5)
												.withTimeout(10)        // TODO this should be graduated - 10 minutes moving up to 30 minutes if fails too many times
												.withParams(RecordStruct.record()
														.with("Id", id)
												)
												.withScript(CommonPath.from("/dcm/store/event-order-updated.dcs.xml")));
									}
									
									callback.returnValue(upresult);
								}
							})
					);
				}
				else {
					callback.returnEmpty();
				}
			}
		}));
	}

	static public void handleAddComment(ServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		String id = data.getFieldAsString("Id");

		ZonedDateTime stamp = TimeUtil.now();

		RecordStruct audit = RecordStruct.record()
				.with("Origin", "Store")
				.with("Stamp", stamp)
				.with("Internal", data.getFieldAsBooleanOrFalse("Internal"))
				.with("Comment", data.getField("Comment"));

		DbRecordRequest upreq = UpdateRecordRequest.update()
				.withTable("dcmOrder")
				.withId(id)
				.withSetField("dcmAudit", TimeUtil.stampFmt.format(stamp), audit);

		ServiceHub.call(upreq
				.toServiceRequest()
				.withOutcome(callback)
		);
	}

	/*
		<StringType Id="dcmOrderStatusEnum">
			<StringRestriction Enum="Pending,AwaitingPayment,AwaitingFulfillment,AwaitingShipment,AwaitingPickup,PartiallyCompleted,Completed,Canceled,VerificationRequired" />
		</StringType>
	
	 */
	static public void handleSearch(ServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();
		
		String term = data.getFieldAsString("Term");
		
		SelectDirectRequest req = new SelectDirectRequest()
				.withTable("dcmOrder")
				.withSelect(new SelectFields()
						.with("Id")
						.with("dcmOrderDate", "OrderDate")
						.with("dcmStatus", "Status")
						.with("dcmCustomerInfo", "CustomerInfo")
						.with("dcmDelivery", "Delivery")
						.with("dcmGrandTotal", "GrandTotal")
				);
		
		if (StringUtil.isDataInteger(term) && (term.length() < 7)) {
			req.withCollector(CollectorField.collect()
					.withField("Id")
					// this assumes a single node server, will fail if order was placed on another node
					.withValues(ApplicationHub.getNodeId() + "_" + StringUtil.leftPad(term, 15, '0'))
			);
		}
		else {
			String[] scope = ("All".equals(data.getFieldAsString("Scope")))
					? new String[] { "AwaitingPayment","AwaitingFulfillment","AwaitingShipment","AwaitingPickup","PartiallyCompleted","Completed" }
					:  new String[] { "AwaitingPayment","AwaitingFulfillment","AwaitingShipment","AwaitingPickup","PartiallyCompleted" };
			
			req.withCollector(CollectorField.collect()
					.withField("dcmStatus")
					.withValues(scope)
			);
			
			String t = StringUtil.stripWhitespace(term);
			
			if (StringUtil.isNotEmpty(t)) {
				req.withWhere(WhereTerm.term()
						.withField("dcmCustomerInfo")
						.withValue(t)
				);
				
				/*
				String[] terms = term.split(" ");
				
				WhereOr where = new WhereOr();
				
				for (String t : terms) {
					String tt = t.trim();
					
					if (StringUtil.isDataInteger(tt))
						WhereUtil.tryWhereContains(where, "dcmKeywords", StringUtil.cleanPhone(tt));
					else
						WhereUtil.tryWhereContains(where, "dcmKeywords", tt.toLowerCase());
				}
				
				if (where.getExpressionCount() > 0)
					req.withWhere(where);
				*/
			}
		}
		
		ServiceHub.call(req.toServiceRequest().withOutcome(callback));
	}
}
