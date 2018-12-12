package dcraft.cms.store;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import dcraft.cms.thread.db.ThreadUtil;
import dcraft.db.Constants;
import dcraft.db.DatabaseException;
import dcraft.db.ICallContext;
import dcraft.db.proc.call.SignIn;
import dcraft.db.request.DataRequest;
import dcraft.db.request.common.AddUserRequest;
import dcraft.db.request.common.RequestFactory;
import dcraft.db.request.query.*;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.request.update.InsertRecordRequest;
import dcraft.db.request.update.UpdateRecordRequest;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.filestore.CommonPath;
import dcraft.filestore.mem.MemoryStoreFile;
import dcraft.filevault.GalleryVault;
import dcraft.filevault.Vault;
import dcraft.filevault.VaultUtil;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.*;
import dcraft.interchange.paypal.PayPalUtil;
import dcraft.interchange.slack.SlackUtil;
import dcraft.interchange.ups.UpsUtil;
import dcraft.log.Logger;
import dcraft.service.ServiceHub;
import dcraft.service.plugin.Operation;
import dcraft.struct.scalar.StringStruct;
import dcraft.task.Task;
import dcraft.task.TaskHub;
import dcraft.tenant.Site;
import dcraft.util.Base64;
import dcraft.util.Memory;
import dcraft.util.TimeUtil;

import dcraft.interchange.authorize.AuthUtil;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;
import dcraft.util.cb.CountDownCallback;
import dcraft.xml.XElement;

public class OrderUtil {
	static public void processAuthOrder(ICallContext request, TablesAdapter db, RecordStruct order, OperationOutcomeStruct callback) throws OperatingContextException {
		Site site = OperationContext.getOrThrow().getSite();
		String event = site.getAlias() + " - order submission started: " + order.getFieldAsRecord("CustomerInfo").toString();
		SlackUtil.serverEvent(null, event, null);
		
		try (OperationMarker om = OperationMarker.create()) {
			order = OrderUtil.santitizeAndCalculateOrder(request, db, order);

			if (om.hasErrors()) {
				callback.returnEmpty();
				return;
			}
		}
		catch (Exception x) {
			Logger.error("OperationMarker error");
			callback.returnEmpty();
			return;
		}

		OrderUtil.processAuthOrder2(request, db, order, callback);
	}

	static public void processAuthOrder2(ICallContext request, TablesAdapter db, RecordStruct order, OperationOutcomeStruct callback) throws OperatingContextException {
		OperationContext context = OperationContext.getOrThrow();

		context.touch();

		ZonedDateTime now = TimeUtil.now();

		RecordStruct orderclean = order.deepCopy();

		// remove sensitive information before saving
		RecordStruct cleanpay = orderclean.getFieldAsRecord("PaymentInfo");

		if (cleanpay != null) {
			cleanpay.removeField("CardNumber");		// TODO keep last 4 digits
			cleanpay.removeField("Expiration");
			cleanpay.removeField("Code");
		}
		
		Site site = OperationContext.getOrThrow().getSite();
		String event = site.getAlias() + " - order processing: " + orderclean.getFieldAsRecord("CustomerInfo").toString();
		SlackUtil.serverEvent(null, event, null);
		
		RecordStruct cinfo = orderclean.getFieldAsRecord("CustomerInfo");
		RecordStruct pinfo = orderclean.getFieldAsRecord("PaymentInfo");

		String keywords = "";

		if (cinfo.isNotFieldEmpty("FirstName"))
			keywords += cinfo.getFieldAsString("FirstName").toLowerCase() + " ";

		if (cinfo.isNotFieldEmpty("LastName"))
			keywords += cinfo.getFieldAsString("LastName").toLowerCase() + " ";

		if (cinfo.isNotFieldEmpty("Email"))
			keywords += cinfo.getFieldAsString("Email").toLowerCase() + " ";

		if (cinfo.isNotFieldEmpty("Phone"))
			keywords += StringUtil.cleanPhone(cinfo.getFieldAsString("Phone")) + " ";

		cinfo.removeField("Password");	// don't store

		String viewcode = StringUtil.buildSecurityCode();

		// insert the order
		DbRecordRequest req = InsertRecordRequest.insert()
			.withTable("dcmOrder")
			.withSetField("dcmOrderDate", now)
			.withSetField("dcmStatus", "AwaitingPayment")
			.withSetField("dcmLastStatusDate", now)
			.withSetField("dcmCustomerInfo", cinfo)
			.withSetField("dcmShippingInfo", orderclean.getField("ShippingInfo"))
			.withSetField("dcmBillingInfo", orderclean.getField("BillingInfo"))
			.withSetField("dcmDelivery", orderclean.getField("Delivery"))
			.withSetField("dcmComment", orderclean.getField("Comment"))
			.withSetField("dcmPaymentInfo", pinfo)
			.withSetField("dcmCalcInfo", orderclean.getField("CalcInfo"))
			.withSetField("dcmExtra", orderclean.getField("Extra"))
			.withSetField("dcmGrandTotal", orderclean.getFieldAsRecord("CalcInfo").getFieldAsDecimal("GrandTotal"))
			.withSetField("dcmKeywords", keywords)
			.withSetField("dcmViewCode", viewcode);

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

		UserContext uctx = context.getUserContext();

		if (cinfo.isNotFieldEmpty("CustomerId"))
			req.withSetField("dcmCustomer", cinfo.getFieldAsString("CustomerId"));
		else if (uctx.isTagged("User"))
			// if this is an authenticated user then we want to track the customer id too
			req.withSetField("dcmCustomer", uctx.getUserId());

		try (OperationMarker om = OperationMarker.create()) {
			String refid = TableUtil.updateRecord(db, req);

			if (om.hasErrors()) {
				callback.returnEmpty();
				return;
			}

			context.touch();

			callback.setResult(RecordStruct.record()
					.with("Id", refid)
					.with("Cart", order)
					.with("ViewCode", viewcode)
			);

			// TODO lookup user and see if they are in "test" mode - this way some people can run test orders through system

			XElement sset = ApplicationHub.getCatalogSettings("CMS-Store");

			if (sset == null) {
				Logger.error("Missing store settings.");
				callback.returnEmpty();
				return;
			}

			String pmethod = cleanpay.getFieldAsString("PaymentMethod", "Manual");

			XElement pel = null;

			for (XElement poel : sset.selectAll("Payment")) {
				String pomode = poel.getAttribute("Alias", poel.getAttribute("Method", "Manual"));

				if (pmethod.equalsIgnoreCase(pomode)) {
					pel = poel;
					break;
				}
			}

			if (pel == null) {
				Logger.error("Missing store payment settings.");
				callback.returnEmpty();
				return;
			}

			pmethod = pel.getAttribute("Method", pmethod);

			DbRecordRequest upreq = UpdateRecordRequest.update()
					.withId(refid)
					.withTable("dcmOrder");

			if ("Manual".equals(pmethod)) {
				upreq.withSetField("dcmStatus", "AwaitingPayment");

				OrderUtil.postAuthStep(request, db, upreq, order, "AwaitingPayment", now, orderclean, null, refid, callback);
			}
			else if ("Authorize".equals(pmethod)) {
				// TODO store order items as independent records? order audits? other fields/tables to fill in?
				// put order into a thread and box

				AuthUtil.authXCard(pel.getAttribute("AuthorizeAlternate"), refid, (RecordStruct) order, new OperationOutcomeRecord() {
					@Override
					public void callback(RecordStruct res) throws OperatingContextException {
						OperationContext.getOrThrow().touch();

						String status = "VerificationRequired";

						if (! this.hasErrors() && ! this.isEmptyResult()) {
							status = "AwaitingFulfillment";
							upreq.withUpdateField("dcmPaymentId", this.getResult().getFieldAsString("TxId"));
						}

						upreq.withUpdateField("dcmStatus", status);

						OrderUtil.postAuthStep(request, db, upreq, order, status, now, orderclean, this.getResult(), refid, callback);
					}
				});
			}
			else if ("PayPal".equals(pmethod)) {
				// check that the PayPal record is present and matching in the payment thread
				try (OperationMarker om2 = OperationMarker.create()) {
					String txid = PayPalUtil.authOrder(request, db, pel.getAttribute("PayPalAlternate"), refid, order);

					String status = "VerificationRequired";

					if (! om2.hasErrors() && StringUtil.isNotEmpty(txid)) {
						status = "AwaitingFulfillment";
						upreq.withUpdateField("dcmPaymentId", txid);
					}

					upreq.withUpdateField("dcmStatus", status);

					OrderUtil.postAuthStep(request, db, upreq, order, status, now, orderclean, StringStruct.of(txid), refid, callback);
				}
				catch (Exception x) { }
			}
		}
		catch (Exception x) {
			Logger.error("OperationMarker error");
			callback.returnEmpty();
			return;
		}
	}

	static public void postAuthStep(ICallContext request, TablesAdapter db, DbRecordRequest upreq, RecordStruct order, String status, ZonedDateTime stamp, RecordStruct orderclean, Struct payment, String refid, OperationOutcomeStruct callback) throws OperatingContextException {
		Site site = OperationContext.getOrThrow().getSite();
		String event = site.getAlias() + " - order placed: " + refid;
		SlackUtil.serverEvent(null, event, null);
		
		// CustomerId already set by sanitize
		if (OperationContext.getOrThrow().getUserContext().isTagged("User")) {
			OrderUtil.onLogStep(request, db, upreq, status, stamp, orderclean, payment, refid, callback);
			return;
		}

		RecordStruct cinfo = order.getFieldAsRecord("CustomerInfo");

		Object userid = null;

		if (cinfo.isNotFieldEmpty("CustomerId")) {
			userid = cinfo.getFieldAsString("CustomerId");

			upreq.withSetField("dcmCustomer", userid);

			OrderUtil.onLogStep(request, db, upreq, status, stamp, orderclean, payment, refid, callback);
			return;
		}
		else {
			userid = db.firstInIndex("dcUser", "dcUsername", cinfo.getFieldAsString("Email").toLowerCase(), true);

			if (userid != null) {
				String cid = userid.toString();

				orderclean.getFieldAsRecord("CustomerInfo").with("CustomerId", cid);
				upreq.withSetField("dcmCustomerInfo", orderclean.getFieldAsRecord("CustomerInfo"));
				upreq.withSetField("dcmCustomer", cid);

				OrderUtil.onLogStep(request, db, upreq, status, stamp, orderclean, payment, refid, callback);
				return;
			}
		}

		if (cinfo.isFieldEmpty("Password")) {
			OrderUtil.onLogStep(request, db, upreq, status, stamp, orderclean, payment, refid, callback);
			return;
		}

		String password = cinfo.getFieldAsString("Password");

		AddUserRequest userrequest = AddUserRequest.of(cinfo.getFieldAsString("Email"))
				.withFirstName(cinfo.getFieldAsString("FirstName"))
				.withLastName(cinfo.getFieldAsString("LastName"))
				.withEmail(cinfo.getFieldAsString("Email"))
				.withPhone(cinfo.getFieldAsString("Phone"))
				.withPassword(password);

		RecordStruct binfo = order.getFieldAsRecord("BillingInfo");

		if (binfo != null)
			userrequest.withConditionallyUpdateFields(binfo, "Address", "dcAddress", "Address2", "dcAddress2",
					"City", "dcCity", "State", "dcState", "Zip", "dcZip");

		String cid = TableUtil.updateRecord(db, userrequest);

		// set user id in order
		orderclean.getFieldAsRecord("CustomerInfo").with("CustomerId", cid);
		upreq.withSetField("dcmCustomerInfo", orderclean.getFieldAsRecord("CustomerInfo"));
		upreq.withSetField("dcmCustomer", cid);

		// sign the user in
		SignIn.signIn(request, db, cid, ! request.isReplicating());

		Logger.info("Customer created from order: " + cid + " logged in: " + OperationContext.getOrThrow().getUserContext().getUsername());

		OrderUtil.onLogStep(request, db, upreq, status, stamp, orderclean, payment, refid, callback);
	}

	static public void onLogStep(ICallContext request, TablesAdapter db, DbRecordRequest upreq, String status, ZonedDateTime stamp, RecordStruct orderclean, Struct payment, String refid, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct audit = RecordStruct.record()
				.with("Origin", "Customer")
				.with("Stamp", stamp)
				.with("Internal", false)
				.with("Comment", "Customer places order")
				.with("Status", status);

		upreq.withSetField("dcmAudit", TimeUtil.stampFmt.format(stamp), audit);

		TableUtil.updateRecord(db, upreq);

		// TODO mark coupons used if present
		/*
		<Field Name="dcmWasUsed" Type="Boolean" />
		<Field Name="dcmAmountUsed" Type="Decimal" />
		*/

		RecordStruct orderlog = RecordStruct.record()
				.with("Order", orderclean)
				.with("PaymentResponse", payment)
				.with("Log", OperationContext.getOrThrow().getController().getMessages());

		// TODO make StoreOrders encrypted file only
		// store in deposit file - TODO make sure this is not expanded locally, should be in encrypted deposit file only
		MemoryStoreFile msource = MemoryStoreFile.of(CommonPath.from("/" + refid + ".json"))
				.with(orderlog.toPrettyString());
		
		VaultUtil.setSessionToken(refid);

		VaultUtil.transfer("StoreOrders", msource, CommonPath.from("/" + refid + ".json"), refid, new OperationOutcomeStruct() {
			@Override
			public void callback(Struct result) throws OperatingContextException {
				//System.out.println(OperationContext.getOrThrow().toPrettyString());

				// TODO trigger Event to send emails separate from order placed so email (notice) can be resent

				// TODO switch to queue local someday soon!
				//TaskHub.queueLocalTask(Task.ofSubtask("Order placed trigger", "STORE")

				TaskHub.submit(Task.ofSubtask("Order placed trigger", "STORE")
						.withTopic("Batch")
						.withMaxTries(5)
						.withTimeout(10)		// TODO this should be graduated - 10 minutes moving up to 30 minutes if fails too many times
						.withParams(RecordStruct.record()
								.with("Id", refid)
						)
						.withScript(CommonPath.from("/dcm/store/event-order-placed.dcs.xml")));
				
				Site site = OperationContext.getOrThrow().getSite();
				String event = site.getAlias() + " - order submission completed: " + refid + " - " + callback.getMessages().toPrettyString();
				SlackUtil.serverEvent(null, event, null);
				
				callback.returnResult();
			}
		});
	}

	static public void trackOrder(ICallContext request, TablesAdapter db, RecordStruct order, OperationOutcomeStruct callback) throws OperatingContextException {
		OperationContext context = OperationContext.getOrThrow();

		context.touch();

		ZonedDateTime now = TimeUtil.now();

		RecordStruct calcinfo = order.getFieldAsRecord("CalcInfo");

		BigDecimal grand = calcinfo.getFieldAsDecimal("GrandTotal");

		DecimalFormat dfmt = new DecimalFormat("0.00");

		String id = ThreadUtil.createThread(db,
				"Order payment tracking: " + dfmt.format(grand),
				false,"dcmOrderPayment", Constants.DB_GLOBAL_ROOT_RECORD, now, null);

		String msg = "Track Amount: $" + dfmt.format(grand);

		ThreadUtil.addContent(db, id, msg, "SafeMD");

		// message is good for 180 days
		db.setStaticScalar("dcmThread", id, "dcmExpireDate", TimeUtil.now().plusDays(180));

		db.setStaticScalar("dcmThread", id, "dcmPaymentAmount", grand);
		db.setStaticScalar("dcmThread", id, "dcmOrderData", order);

		// TODO review
		//ThreadUtil.addParty(db, id, "/CSPool", "/InBox", null);
		//
		//ThreadUtil.deliver(db, id, future);

		callback.returnValue(RecordStruct.record()
				.with("Uuid", db.getStaticScalar("dcmThread", id, "dcmUuid"))
				.with("Cart", order)
		);
	}

	static public void createShipment(String id, RecordStruct shipment, ListStruct items, OperationOutcomeStruct callback) throws OperatingContextException {
		LoadRecordRequest recordRequest = LoadRecordRequest.of("dcmOrder")
				.withId(id)
				.withSelect(SelectFields.select()
						.with("dcmCustomerInfo", "CustomerInfo")
						.with("dcmShippingInfo", "ShippingInfo")
				);
		
		ServiceHub.call(recordRequest, new OperationOutcomeStruct() {
			@Override
			public void callback(Struct result) throws OperatingContextException {
				if (this.hasErrors()) {
					callback.returnEmpty();
					return;
				}
				
				RecordStruct orderRec = Struct.objectToRecord(result);
				
				// if shipment doesn't have a destination, copy from order
				if (shipment.isFieldEmpty("Address")) {
					shipment.copyFields(orderRec.getFieldAsRecord("ShippingInfo"));
				}
				
				RecordStruct labelData = RecordStruct.record()
						.with("ShipmentInfo", shipment)
						.with("CustomerInfo", orderRec.getFieldAsRecord("CustomerInfo"));
				
				String provider = shipment.getFieldAsString("ShipProvider");
				
				if ("UPS".equals(provider)) {
					UpsUtil.createLabel(shipment.getFieldAsString("Alternate"), labelData, shipment.getFieldAsString("ShipService"), new OperationOutcomeRecord() {
						@Override
						public void callback(RecordStruct result) throws OperatingContextException {
							if (this.isEmptyResult()) {
								Logger.error("Shipment request failed.");
								return;
							}
							
							String respcode = result.selectAsString("ShipmentResponse.Response.ResponseStatus.Code");
							
							if (! "1".equals(respcode)) {
								Logger.error("Shipment request errored.");
								Logger.error("UPS Code: " + result.selectAsString("Fault.detail.Errors.ErrorDetail.PrimaryErrorCode.Code"));
								Logger.error("UPS Msg: " + result.selectAsString("Fault.detail.Errors.ErrorDetail.PrimaryErrorCode.Description"));
							}
							else {
								BigDecimal amt = result.selectAsDecimal("ShipmentResponse.ShipmentResults.ShipmentCharges.TotalCharges.MonetaryValue");
								String tracking = result.selectAsString("ShipmentResponse.ShipmentResults.ShipmentIdentificationNumber");
								String image = result.selectAsString("ShipmentResponse.ShipmentResults.PackageResults.ShippingLabel.GraphicImage");
								String thumb = result.selectAsString("ShipmentResponse.ShipmentResults.PackageResults.ShippingLabel.HTMLImage");
								
								shipment
										.with("Cost", amt)
										.with("Purchased", TimeUtil.now())
										.with("TrackId", tracking)
										.with("TrackLink", "https://wwwapps.ups.com/WebTracking/track?track=yes&trackNums=" + tracking);
								
								RecordStruct orderlog = RecordStruct.record()
										.with("ShipmentResponse", result)
										.with("Log", OperationContext.getOrThrow().getController().getMessages());
								
								// TODO trigger Event to send emails
								
								// store in deposit file - TODO make sure this is not expanded locally, should be in encrypted deposit file only
								CommonPath logpath = CommonPath.from("/" + id + "/ship-" + shipment.getFieldAsString("EntryId") + ".json");
								
								MemoryStoreFile msource = MemoryStoreFile.of(logpath)
										.with(orderlog.toPrettyString());
								
								// TODO combine these into 1 vault transaction
								VaultUtil.transfer("StoreOrders", msource, logpath, id, new OperationOutcomeStruct() {
									@Override
									public void callback(Struct result) throws OperatingContextException {
										//System.out.println(OperationContext.getOrThrow().toPrettyString());
										
										CommonPath imgpath = CommonPath.from("/" + id + "/ship-" + shipment.getFieldAsString("EntryId") + "-label.gif");
										
										MemoryStoreFile msource = MemoryStoreFile.of(imgpath)
												.with(new Memory(Base64.decodeFast(image)));
										
										VaultUtil.transfer("StoreOrders", msource, imgpath, id, new OperationOutcomeStruct() {
											@Override
											public void callback(Struct result) throws OperatingContextException {
												//System.out.println(OperationContext.getOrThrow().toPrettyString());
												/*
												CommonPath thmpath = CommonPath.from("/" + id + "/ship-" + shipment.getFieldAsString("EntryId") + "-thumb.gif");
												
												MemoryStoreFile msource = MemoryStoreFile.of(thmpath)
														.with(new Memory(Base64.decodeFast(thumb)));
												
												VaultUtil.transfer("StoreOrders", msource, thmpath, new OperationOutcomeStruct() {
													@Override
													public void callback(Struct result) throws OperatingContextException {
														//System.out.println(OperationContext.getOrThrow().toPrettyString());
														*/

														// update the database
														DataRequest record = DataRequest.of("dcmRecordShipment")
																.withParam("Id", id)
																.withParam("Shipment", shipment)
																.withParam("Items", items);
														
														ServiceHub.call(record, callback);
														/*
													}
												});
												*/
											}
										});
									}
								});
							}
						}
					});
				}
				else {
					Logger.error("Ship provider not supported");
					callback.returnEmpty();
					return;
				}
			}
		});
	}

	static public RecordStruct santitizeAndCalculateOrder(ICallContext request, TablesAdapter db, RecordStruct order) throws OperatingContextException {
		// -------------------------------------------
		// be sure that the customer info is good
		RecordStruct custinfo = order.getFieldAsRecord("CustomerInfo");		// required

		UserContext uctx = OperationContext.getOrThrow().getUserContext();

		if (custinfo != null) {
			// if this is an authenticated user then we want to track the customer id too
			if (uctx.isTagged("User"))
				custinfo.with("CustomerId", uctx.getUserId());
			else
				custinfo.removeField("CustomerId");
		}
		else if (uctx.isTagged("User")) {
			custinfo = RecordStruct.record()
					.with("FirstName", uctx.getFirstName())
					.with("LastName", uctx.getLastName())
					.with("Email", uctx.getEmail())
					.with("CustomerId", uctx.getUserId());

			order.with("CustomerInfo", custinfo);
		}

		// -------------------------------------------
		// check products are real and priced right 
		
		ListStruct items = order.getFieldAsList("Items");

		// if we find items from submitted list in the database then add those items to our true item list
		ListStruct fnditems = new ListStruct();

		AtomicReference<BigDecimal> itmcalc = new AtomicReference<>(BigDecimal.ZERO);
		AtomicReference<BigDecimal> taxcalc = new AtomicReference<>(BigDecimal.ZERO);
		//AtomicReference<BigDecimal> shipcalc = new AtomicReference<>(BigDecimal.ZERO);

		AtomicReference<BigDecimal> itemshipcalc = new AtomicReference<>(BigDecimal.ZERO);
		//AtomicReference<BigDecimal> catshipcalc = new AtomicReference<>(BigDecimal.ZERO);
		AtomicReference<BigDecimal> itemshipweight = new AtomicReference<>(BigDecimal.ZERO);

		AtomicReference<BigDecimal> shipamt = new AtomicReference<>(BigDecimal.ZERO);

		AtomicReference<BigDecimal> itmdiscount = new AtomicReference<>(BigDecimal.ZERO);
		AtomicReference<BigDecimal> shipdiscount = new AtomicReference<>(BigDecimal.ZERO);

		XElement sset = ApplicationHub.getCatalogSettings("CMS-Store");

		if (sset == null) {
			Logger.error("Missing store settings.");
			return null;
		}

		String orderdelivery = order.getFieldAsString("Delivery");

		SelectFields selectFields = SelectFields.select()
				.with("Id", "Product")
				.with("dcmDisabled", "Disabled")
				.with("dcmTitle", "Title")
				.with("dcmAlias", "Alias")
				.with("dcmSku", "Sku")
				.with("dcmDescription", "Description")
				.with("dcmInstructions", "Instructions")
				.with("dcmDelivery", "Delivery")
				.with("dcmImage", "Image")
				.withComposer("dcmStoreImage",  "ImagePath")
				.with("dcmTag", "Tags")
				.with("dcmVariablePrice", "VariablePrice")
				.with("dcmMinimumPrice", "MinimumPrice")
				.with("dcmPrice", "Price")
				.with("dcmSalePrice", "SalePrice")
				.with("dcmTaxFree", "TaxFree")
				.with("dcmShipCost", "ShipCost")
				.with("dcmShipAmount", "ShipAmount")
				.with("dcmShipWeight", "ShipWeight")
				.withForeignField("dcmCategory", "CatShipAmount", "dcmShipAmount");

		for (Struct itm : items.items()) {
			RecordStruct orgitem = Struct.objectToRecord(itm);

			String prodid = orgitem.getFieldAsString("Product");

			// grab weight / ship cost from here but don't store in order info
			// TODO consider dcmPurchaseOnline or dcmDisabled

			RecordStruct prodrec = TableUtil.getRecord(db, OperationContext.getOrThrow(), "dcmProduct", prodid, selectFields);

			if (prodrec.getFieldAsBooleanOrFalse("Disabled"))
				continue;

			RecordStruct item = orgitem.deepCopy();

			// make sure we are using the 'real' values here, from the DB
			// we are excluding fields that are not valid in an order item, however these fields are still used in the calculation below
			item.copyFields(prodrec, "ShipAmount", "ShipWeight", "CatShipAmount", "Disabled");

			// add to the 'real' order list
			fnditems.with(item);

			BigDecimal price = item.isFieldEmpty("SalePrice")
					? item.getFieldAsDecimal("Price", BigDecimal.ZERO) : item.getFieldAsDecimal("SalePrice");

			if (prodrec.getFieldAsBooleanOrFalse("VariablePrice")) {
				price = orgitem.getFieldAsDecimal("Price", BigDecimal.ZERO);

				BigDecimal min = prodrec.getFieldAsDecimal("MinimumPrice", BigDecimal.ZERO);

				if (price.compareTo(min) < 0)
					price = min;

				// sale price not appropriate on variable priced items
				item.with("Price", price);
				item.removeField("SalePrice");
			}

			BigDecimal qty = item.getFieldAsDecimal("Quantity", BigDecimal.ZERO);
			BigDecimal total = price.multiply(qty);

			item.with("Total", total);

			itmcalc.set(itmcalc.get().add(total));

			if (!item.getFieldAsBooleanOrFalse("TaxFree"))
				taxcalc.set(taxcalc.get().add(total));

			String shipcost = item.getFieldAsString("ShipCost", "Regular");
			ListStruct delivery = item.getFieldAsList("Delivery");

			if ("Ship".equals(orderdelivery)) {
				for (int d = 0; d < delivery.getSize(); d++) {
					if (!"Ship".equals(delivery.getItemAsString(d)))
						continue;

					// shipcost: Regular,Extra,Fixed,Free

					if ("Fixed".equals(shipcost))
						itemshipcalc.set(itemshipcalc.get().add(prodrec.getFieldAsDecimal("ShipAmount", BigDecimal.ZERO).multiply(qty)));
					else if ("Extra".equals(shipcost))    // TODO make it so Extra is in addition to normal shipping amt
						itemshipcalc.set(itemshipcalc.get().add(prodrec.getFieldAsDecimal("ShipAmount", BigDecimal.ZERO).multiply(qty)));

					// only include weight if not Free or Fixed
					if ("Regular".equals(shipcost) || "Extra".equals(shipcost)) {
						BigDecimal shipweight = prodrec.getFieldAsDecimal("ShipWeight", BigDecimal.ONE);        // default to 1 ounce
						itemshipweight.set(itemshipweight.get().add(shipweight.multiply(qty)));
					}

					//if (rec.isNotFieldEmpty("CatShipAmount"))
					//	catshipcalc.set(catshipcalc.get().add(rec.getFieldAsDecimal("CatShipAmount").multiply(qty)));

					break;
				}
			}
		}

		// replace the proposed items with the found and cleaned items
		order.with("Items", fnditems);

		/* Enum="Disabled,OrderWeight,OrderTotal,PerItem,PerItemFromCategory,Custom"
		 */

		/*
		String shipmode = "Disabled";

		// shipping is based on Order Total before discounts
		if (shipsettings != null) {
			shipmode = shipsettings.getAttribute("Mode", shipmode);

			// TODO if OrderWeight,OrderTotal then do a table lookup in shipsettings

			// TODO if custom then harass the domain watcher with a shipping calc

			if ("PerItem".equals(shipmode))
				shipamt.set(itemshipcalc.get());
			else if ("PerItemFromCategory".equals(shipmode))
				shipamt.set(catshipcalc.get());
		}
		*/

		// ship calcs

		shipamt.set(itemshipcalc.get());

		XElement shipsettings = sset.selectFirst("Shipping");
		boolean shiptax = false;

		// if settings, and any weight at all (note non-shippable and fixed shipping items do not add weight)
		if ((shipsettings != null) && (itemshipweight.get().compareTo(BigDecimal.ZERO) > 0)) {
			for (XElement stel : shipsettings.selectAll("WeightTable/Limit")) {
				BigDecimal max = Struct.objectToDecimal(stel.getAttribute("Max"));

				// if fall in the range, then add
				if ((max != null) && (itemshipweight.get().compareTo(max) < 0)) {
					BigDecimal shipat = Struct.objectToDecimal(stel.getAttribute("Price"));

					if (shipat != null)
						shipamt.set(shipamt.get().add(shipat));

					break;
				}
			}

			shiptax = shipsettings.getAttributeAsBooleanOrFalse("Taxable");
		}
		
		// discount calcs
		
		// TODO support Sale,GiftCertificate,Credit
		String couponcode = null;
		
		if (order.isNotFieldEmpty("CouponCodes")) {
			// TODO only one supported at this time
			ListStruct codes = order.selectAsList("CouponCodes");

			if (codes.size() > 0)
				couponcode = codes.getItemAsString(0);
		}
		
		if (StringUtil.isNotEmpty(couponcode)) {
			Object dsv = db.firstInIndex("dcmDiscount", "dcmCode", couponcode.trim(), true);
			
			if (dsv != null) {
				String discid = dsv.toString();
				
				if (Struct.objectToBooleanOrFalse(db.getStaticScalar("dcmDiscount", discid, "dcmActive"))) {
					ZonedDateTime start = Struct.objectToDateTime(db.getStaticScalar("dcmDiscount", discid, "dcmStart"));
					ZonedDateTime expire = Struct.objectToDateTime(db.getStaticScalar("dcmDiscount", discid, "dcmExpire"));
					
					ZonedDateTime now = TimeUtil.now();
					
					if (((start == null) || start.isBefore(now)) && ((expire == null) || expire.isAfter(now))) {
						BigDecimal min = Struct.objectToDecimal(db.getStaticScalar("dcmDiscount", discid, "dcmMinimumOrder"));
						
						if ((min == null) || (itmcalc.get().compareTo(min) >= 0)) {
							// TODO ignore "dcmType" for now, if it has a code then it logically is a coupon
							
							// ignore FixedOffTotal for now, FixedOffProduct seems appropriate
							
							String mode = Struct.objectToString(db.getStaticScalar("dcmDiscount", discid, "dcmMode"));
							BigDecimal amt = Struct.objectToDecimal(db.getStaticScalar("dcmDiscount", discid, "dcmAmount"));
							
							if ("FixedOffProduct".equals(mode))
								itmdiscount.set(amt);
							
							// amount = % off as in 20 for 20% off
							if ("PercentOffProduct".equals(mode))
								itmdiscount.set(itmcalc.get().multiply(amt).divide(BigDecimal.valueOf(100), RoundingMode.HALF_UP));
							
							if ("FixedOffShipping".equals(mode))
								shipdiscount.set(amt);
							
							// amount = % off as in 20 for 20% off
							if ("PercentOffShipping".equals(mode))
								shipdiscount.set(shipamt.get().multiply(amt).divide(BigDecimal.valueOf(100), RoundingMode.HALF_UP));
							
							if ("FlatShipping".equals(mode)) {
								// even it out so that we add/subtract to a level
								shipdiscount.set(shipamt.get().subtract(amt));
								
								if (shipdiscount.get().stripTrailingZeros().compareTo(BigDecimal.ZERO) < 0)
									shipdiscount.set(shipdiscount.get().negate());
							}
							
							if ("FreeShipping".equals(mode))
								shipdiscount.set(shipamt.get());
						}
					}
				}
			}
		}

		BigDecimal itmtotal = itmcalc.get().add(itmdiscount.get().negate());

		if (itmtotal.stripTrailingZeros().compareTo(BigDecimal.ZERO) < 0)
			itmtotal = BigDecimal.ZERO;

		BigDecimal shiptotal = shipamt.get().add(shipdiscount.get().negate());

		if (shiptotal.stripTrailingZeros().compareTo(BigDecimal.ZERO) < 0)
			shiptotal = BigDecimal.ZERO;

		if (shiptax)
			taxcalc.set(taxcalc.get().add(shiptotal));

		// look up taxes
		BigDecimal taxat = BigDecimal.ZERO;

		XElement taxtable = sset.selectFirst("TaxTable");

		if (taxtable != null) {
			String state = null;

			RecordStruct shipinfo = order.getFieldAsRecord("ShippingInfo");	// not required

			if ((shipinfo != null) && !shipinfo.isFieldEmpty("State"))
				state = shipinfo.getFieldAsString("State");

			if (StringUtil.isEmpty(state)) {
				RecordStruct billinfo = order.getFieldAsRecord("BillingInfo");	// not required

				if ((billinfo != null) && !billinfo.isFieldEmpty("State"))
					state = billinfo.getFieldAsString("State");
			}

			if (StringUtil.isEmpty(state) && "Pickup".equals(order.getFieldAsString("Delivery")))
				state = sset.getAttribute("PickupState");

			if (StringUtil.isNotEmpty(state)) {
				for (XElement stel : taxtable.selectAll("State")) {
					if (state.equals(stel.getAttribute("Alias"))) {
						taxat = new BigDecimal(stel.getAttribute("Rate", "0.0"));
						break;
					}
				}
			}
		}

		// TODO account for product discounts in taxcalc, apply discounts to the taxfree part first then reduce taxcalc by any remaining discount amt
		BigDecimal taxtotal = taxcalc.get().multiply(taxat).setScale(2, RoundingMode.HALF_EVEN);

		if ((custinfo != null) && custinfo.getFieldAsBooleanOrFalse("TaxExempt") && sset.getAttributeAsBooleanOrFalse("EnableTaxExempt"))
			taxtotal = BigDecimal.ZERO;

		// correct order calculations, totals
		RecordStruct calcinfo = new RecordStruct()
				.with("ItemCalc", itmcalc.get())
				.with("ProductDiscount", itmdiscount.get())
				.with("ItemTotal", itmtotal)
				.with("ShipCalc", shipamt.get())
				.with("ShipAmount", shipamt.get())
				.with("ShipDiscount", shipdiscount.get())
				.with("ShipTotal", shiptotal)
				.with("TaxCalc", taxcalc.get())
				.with("TaxAt", taxat)
				.with("TaxTotal", taxtotal)
				.with("GrandTotal", itmtotal.add(shiptotal).add(taxtotal));

		order.with("CalcInfo", calcinfo);

		return order;

		/*

		// TODO add discount support

		// coupons
		*/
	}
}
