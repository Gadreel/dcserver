package dcraft.cms.store.work;

import dcraft.hub.op.OperatingContextException;
import dcraft.struct.RecordStruct;
import dcraft.task.StateWork;
import dcraft.task.StateWorkStep;
import dcraft.task.TaskContext;
import dcraft.util.TimeUtil;

import java.time.ZonedDateTime;

public class PlaceOrderWork extends StateWork {
	protected ZonedDateTime now = TimeUtil.now();
	
	protected StateWorkStep insertstep = null;
	protected StateWorkStep paystep = null;
	protected StateWorkStep couponstep = null;
	protected StateWorkStep depositstep = null;
	protected StateWorkStep noticestep = null;
	protected StateWorkStep donestep = null;

	protected RecordStruct paymentresp = null;

	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		/*
		this
				.withStep(StateWorkStep.of("Sanitise Order", this::sanitise))
				.withStep(insertstep = StateWorkStep.of("Insert Order", this::insert))
				.withStep(paystep = StateWorkStep.of("Process Payment", this::payment))
				.withStep(couponstep = StateWorkStep.of("Record Coupons", this::coupons))
				.withStep(depositstep = StateWorkStep.of("Create Deposit", this::deposit))
				.withStep(noticestep = StateWorkStep.of("Send Notices", this::notices))
				.withStep(donestep = StateWorkStep.of("Complete", this::done));
				*/
		
		this.failOnErrors = false;
	}

	/*
	public StateWorkStep sanitise(TaskContext trun) throws OperatingContextException {
		RecordStruct order = trun.selectAsRecord("Params.Order");
		
		OrderUtil.santitizeAndCalculateOrder(order, new OperationOutcomeStruct() {
			@Override
			public void callback(Struct order) throws OperatingContextException {
				if (this.hasErrors()) {
					PlaceOrderWork.this.transition(trun, donestep);
					return;
				}
				
				// replace the order with the sanitised version
				trun.selectAsRecord("Params").with("Order", order);
				
				PlaceOrderWork.this.transition(trun, insertstep);
			}
		});
		
		return StateWorkStep.WAIT;
	}
	
	public StateWorkStep insert(TaskContext trun) throws OperatingContextException {
		RecordStruct order = trun.selectAsRecord("Params.Order");
		
		RecordStruct orderclean = order.deepCopy();
		
		trun.selectAsRecord("Params").with("CleanOrder", orderclean);
		
		// remove sensitive information before saving
		RecordStruct cleanpay = orderclean.getFieldAsRecord("PaymentInfo");
		
		if (cleanpay != null) {
			cleanpay.removeField("CardNumber");		// TODO keep last 4 digits
			cleanpay.removeField("Expiration");
			cleanpay.removeField("Code");
		}
		
		// insert the order
		DbRecordRequest req = InsertRecordRequest.insert()
				.withTable("dcmOrder")
				.withSetField("dcmOrderDate", now)
				.withSetField("dcmStatus", "AwaitingPayment")
				.withSetField("dcmLastStatusDate", now)
				.withSetField("dcmCustomerInfo", orderclean.getField("CustomerInfo"))
				.withSetField("dcmShippingInfo", orderclean.getField("ShippingInfo"))
				.withSetField("dcmBillingInfo", orderclean.getField("BillingInfo"))
				.withSetField("dcmDelivery", orderclean.getField("Delivery"))
				.withSetField("dcmComment", orderclean.getField("Comment"))
				.withSetField("dcmPaymentInfo", orderclean.getField("PaymentInfo"))
				.withSetField("dcmCalcInfo", orderclean.getField("CalcInfo"))
				.withSetField("dcmExtra", orderclean.getField("Extra"))
				.withSetField("dcmGrandTotal", orderclean.getFieldAsRecord("CalcInfo").getFieldAsDecimal("GrandTotal"));
		
		if (orderclean.isNotFieldEmpty("Items")) {
			for (Struct iteme : orderclean.selectAsList("Items").items()) {
				RecordStruct item = Struct.objectToRecord(iteme);

				String eid = item.getFieldAsString("EntryId");

				req
						.withSetField("dcmItemEntryId", eid, eid)
						.withSetField("dcmItemProduct", eid, item.getFieldAsString("Product"))
						.withSetField("dcmItemQuantity", eid, item.getFieldAsInteger("Quantity"))
						.withSetField("dcmItemPrice", eid, item.getFieldAsDecimal("Price"))
						.withSetField("dcmItemTotal", eid, item.getFieldAsDecimal("Total"))
						.withSetField("dcmItemStatus", eid, "AwaitingFulfillment");

				if (item.isNotFieldEmpty("Extra"))
					req.withSetField("dcmItemExtra", eid, item.getFieldAsRecord("Extra"));
			}
		}
		
		if (orderclean.isNotFieldEmpty("CouponCodes")) {
			for (Struct code : orderclean.selectAsList("CouponCodes").items()) {
				req.withSetField("dcmCouponCodes", code.toString(), code);
			}
		}
		
		if (orderclean.isNotFieldEmpty("Discounts")) {
			for (Struct discount : orderclean.selectAsList("Discounts").items()) {
				req.withSetField("dcmDiscounts", ((RecordStruct)discount).selectAsString("EntryId"), discount);
			}
		}
		
		UserContext uctx = OperationContext.getOrThrow().getUserContext();
		
		// if this is an authenticated user then we want to track the customer id too
		if (uctx.isTagged("User"))
			req.withSetField("dcmCustomer", uctx.getUserId());
		
		ServiceHub.call(req.toServiceRequest().withOutcome(new OperationOutcomeStruct() {
			@Override
			public void callback(Struct result) throws OperatingContextException {
				if (this.hasErrors()) {
					PlaceOrderWork.this.transition(trun, donestep);
					return;
				}
				
				RecordStruct resultrec = (RecordStruct) result;
				
				resultrec.with("Cart", trun.selectAsRecord("Params.Order"));
				
				trun.setResult(resultrec);
				
				PlaceOrderWork.this.transition(trun, paystep);
			}
		}));
		
		return StateWorkStep.WAIT;
	}
	
	public StateWorkStep payment(TaskContext trun) throws OperatingContextException {
		// TODO lookup user and see if they are in "test" mode - this way some people can run test orders through system
		
		XElement sset = ApplicationHub.getCatalogSettings("CMS-Store");
		
		if (sset == null) {
			Logger.error("Missing store settings.");
			return donestep;
		}
		
		RecordStruct resultrec = trun.selectAsRecord("Result");
		
		String refid = resultrec.getFieldAsString("Id");
		
		XElement pel = sset.selectFirst("Payment");
		
		String pmode = (pel != null) ? pel.getAttribute("Method", "Manual") : "Manual";
		
		if ("Manual".equals(pmode)) {
			DbRecordRequest upreq = UpdateRecordRequest.update()
					.withId(refid)
					.withTable("dcmOrder");
			
			upreq.withSetField("dcmStatus", "AwaitingPayment");
			
			ServiceHub.call(upreq.toServiceRequest().withOutcome(new OperationOutcomeStruct() {
				@Override
				public void callback(Struct result) throws OperatingContextException {
					if (this.hasErrors()) {
						PlaceOrderWork.this.transition(trun, donestep);
						return;
					}
					
					PlaceOrderWork.this.transition(trun, couponstep);
				}
			}));
		}
		else if ("Authorize".equals(pmode)) {
			// TODO store order items as independent records? order audits? other fields/tables to fill in?
			// put order into a thread and box
			
			AuthUtil.authXCard(pel.getAttribute("AuthorizeAlternate"), refid, resultrec.getFieldAsRecord("Cart"), new OperationOutcomeRecord() {
				@Override
				public void callback(RecordStruct res) throws OperatingContextException {
					DbRecordRequest upreq = UpdateRecordRequest.update()
							.withId(refid)
							.withTable("dcmOrder");

					if (this.hasErrors() || this.isEmptyResult())
						upreq
								.withUpdateField("dcmStatus", "VerificationRequired");
					else
						upreq
								.withUpdateField("dcmStatus", "AwaitingFulfillment")
								.withUpdateField("dcmPaymentId", this.getResult().getFieldAsString("TxId"));

					PlaceOrderWork.this.paymentresp = this.getResult();

					ServiceHub.call(upreq.toServiceRequest().withOutcome(new OperationOutcomeStruct() {
						@Override
						public void callback(Struct result) throws OperatingContextException {
							if (this.hasErrors()) {
								PlaceOrderWork.this.transition(trun, donestep);
								return;
							}

							PlaceOrderWork.this.transition(trun, couponstep);
						}
					}));
				}
			});
		}
		
		return StateWorkStep.WAIT;
	}
	
	public StateWorkStep coupons(TaskContext trun) throws OperatingContextException {
		
		// TODO mark coupons used if present
									    	/*
											<Field Name="dcmWasUsed" Type="Boolean" />
											<Field Name="dcmAmountUsed" Type="Decimal" />
									    	* /
		return StateWorkStep.NEXT;
	}
	
	public StateWorkStep deposit(TaskContext trun) throws OperatingContextException {
		RecordStruct orderclean = trun.selectAsRecord("Params.CleanOrder");
		RecordStruct resultrec = trun.selectAsRecord("Result");
		
		String refid = resultrec.getFieldAsString("Id");

		RecordStruct orderlog = RecordStruct.record()
				.with("Order", orderclean)
				.with("PaymentResponse", this.paymentresp)
				.with("Log", OperationContext.getOrThrow().getController().getMessages());

		// store in deposit file - TODO make sure this is not expanded locally, should be in encrypted deposit file only
		MemoryStoreFile msource = MemoryStoreFile.of(CommonPath.from("/" + refid + ".json"))
				.with(orderlog.toPrettyString());
		
		VaultUtil.transfer("StoreOrders", msource, CommonPath.from("/" + refid + ".json"), new OperationOutcomeStruct() {
			@Override
			public void callback(Struct result) throws OperatingContextException {
				PlaceOrderWork.this.transition(trun, noticestep);
			}
		});
		
		return StateWorkStep.WAIT;
	}
	
	public StateWorkStep notices(TaskContext trun) throws OperatingContextException {
		TaskHub.queueLocalTask(Task.ofSubtask("Order placed trigger", "STORE")
				.withTopic("Batch")
				.withMaxTries(5)
				.withTimeout(10)		// TODO this should be graduated - 10 minutes moving up to 30 minutes if fails too many times
				.withParams(RecordStruct.record()
						.with("Id", trun.selectAsRecord("Result").getFieldAsString("Id"))
				)
				.withScript(CommonPath.from("/dcm/store/event-order-placed.dcs.xml")));
		
		return StateWorkStep.NEXT;
	}
	
	public StateWorkStep done(TaskContext trun) throws OperatingContextException {
		// TODO mark the order as retired if we didn't pass the payment step
		
		return StateWorkStep.NEXT;
	}
	*/
}
