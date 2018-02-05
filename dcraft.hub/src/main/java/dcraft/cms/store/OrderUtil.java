package dcraft.cms.store;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import dcraft.db.DatabaseException;
import dcraft.db.request.DataRequest;
import dcraft.db.request.common.AddUserRequest;
import dcraft.db.request.common.RequestFactory;
import dcraft.db.request.query.*;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.request.update.InsertRecordRequest;
import dcraft.db.request.update.UpdateRecordRequest;
import dcraft.filestore.CommonPath;
import dcraft.filestore.mem.MemoryStoreFile;
import dcraft.filevault.GalleryVault;
import dcraft.filevault.VaultUtil;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.*;
import dcraft.interchange.ups.UpsUtil;
import dcraft.log.Logger;
import dcraft.service.ServiceHub;
import dcraft.service.plugin.Operation;
import dcraft.task.Task;
import dcraft.task.TaskHub;
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
	static public void processAuthOrder(RecordStruct order, OperationOutcomeStruct callback) throws OperatingContextException {
		OrderUtil.santitizeAndCalculateOrder(order, new OperationOutcomeStruct() {
			@Override
			public void callback(Struct order) throws OperatingContextException {
				OperationContext context = OperationContext.getOrThrow();

				context.touch();
		    	
				if (this.hasErrors()) {
					callback.returnEmpty();
					return;
				}
				
				ZonedDateTime now = TimeUtil.now();
				
				RecordStruct orderclean = ((RecordStruct) order).deepCopy();
				
				// remove sensitive information before saving
				RecordStruct cleanpay = orderclean.getFieldAsRecord("PaymentInfo");
				
				if (cleanpay != null) {
					cleanpay.removeField("CardNumber");		// TODO keep last 4 digits
					cleanpay.removeField("Expiration");
					cleanpay.removeField("Code");
				}
				
				RecordStruct cinfo = orderclean.getFieldAsRecord("CustomerInfo");
				
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
					.withSetField("dcmPaymentInfo", orderclean.getField("PaymentInfo"))
					.withSetField("dcmCalcInfo", orderclean.getField("CalcInfo"))
					.withSetField("dcmExtra", orderclean.getField("Extra"))
					.withSetField("dcmGrandTotal", orderclean.getFieldAsRecord("CalcInfo").getFieldAsDecimal("GrandTotal"))
					.withSetField("dcmKeywords", keywords);

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
				
				// if this is an authenticated user then we want to track the customer id too
				if (uctx.isTagged("User"))
					req.withSetField("dcmCustomer", uctx.getUserId());

				ServiceHub.call(req.toServiceRequest().withOutcome(new OperationOutcomeStruct() {
					@Override
					public void callback(Struct result) throws OperatingContextException {
				    	OperationContext context = OperationContext.getOrThrow();

				    	context.touch();
				    	
						if (this.hasErrors()) {
							callback.returnEmpty();
							return;
						}
						
						RecordStruct resultrec = (RecordStruct) result;
						
						String refid = resultrec.getFieldAsString("Id");

						resultrec.with("Cart", order);

						callback.setResult(resultrec);

						// TODO lookup user and see if they are in "test" mode - this way some people can run test orders through system
						
						XElement sset = ApplicationHub.getCatalogSettings("CMS-Store");
						
						if (sset == null) {
							Logger.error("Missing store settings.");
							callback.returnEmpty();
							return;
						}

						XElement pel = sset.selectFirst("Payment");
						
						String pmode = (pel != null) ? pel.getAttribute("Method", "Manual") : "Manual";
						
						if ("Manual".equals(pmode)) {
							DbRecordRequest upreq = UpdateRecordRequest.update()
								.withId(refid)
								.withTable("dcmOrder");
						
							upreq.withSetField("dcmStatus", "AwaitingPayment");

							OrderUtil.postAuthStep(upreq, (RecordStruct) order, "AwaitingPayment", now, orderclean, null, refid, callback);
						}
						else if ("Authorize".equals(pmode)) {
							// TODO store order items as independent records? order audits? other fields/tables to fill in?
							// put order into a thread and box
							
							AuthUtil.authXCard(pel.getAttribute("AuthorizeAlternate"), refid, (RecordStruct) order, new OperationOutcomeRecord() {
								@Override
								public void callback(RecordStruct res) throws OperatingContextException {
							    	OperationContext.getOrThrow().touch();
							    	
									DbRecordRequest upreq = UpdateRecordRequest.update()
										.withId(refid)
										.withTable("dcmOrder");

									String status = "VerificationRequired";

									if (this.hasErrors() || this.isEmptyResult()) {
										upreq
												.withUpdateField("dcmStatus", "VerificationRequired");
									}
									else {
										upreq
												.withUpdateField("dcmStatus", "AwaitingFulfillment")
												.withUpdateField("dcmPaymentId", this.getResult().getFieldAsString("TxId"));

										status = "AwaitingFulfillment";
									}

									OrderUtil.postAuthStep(upreq, (RecordStruct) order, status, now, orderclean, this.getResult(), refid, callback);
								}
							});
						}
					}
				}));
			}
		});
	}

	static public void postAuthStep(DbRecordRequest upreq, RecordStruct order, String status, ZonedDateTime stamp, RecordStruct orderclean, Struct payment, String refid, OperationOutcomeStruct callback) throws OperatingContextException {
		if (OperationContext.getOrThrow().getUserContext().isTagged("User")) {
			OrderUtil.onLogStep(upreq, status, stamp, orderclean, payment, refid, callback);
			return;
		}

		RecordStruct cinfo = order.getFieldAsRecord("CustomerInfo");

		SelectDirectRequest selectDirectRequest = SelectDirectRequest.of("dcUser")
				.withSelect(SelectFields.select().with("Id"))
				.withCollector(CollectorField.collect().withField("dcUsername").withValues(cinfo.getFieldAsString("Email")));

		ServiceHub.call(selectDirectRequest.toServiceRequest().withOutcome(new OperationOutcomeStruct() {
			@Override
			public void callback(Struct selectresult) throws OperatingContextException {
				OperationContext.getOrThrow().touch();

				if (this.isNotEmptyResult()) {
					String cid = Struct.objectToList(selectresult).getItemAsRecord(0).getFieldAsString("Id");

					orderclean.getFieldAsRecord("CustomerInfo").with("CustomerId", cid);
					upreq.withSetField("dcmCustomerInfo", orderclean.getFieldAsRecord("CustomerInfo"));
					upreq.withSetField("dcmCustomer", cid);

					OrderUtil.onLogStep(upreq, status, stamp, orderclean, payment, refid, callback);
					return;
				}

				if (cinfo.isFieldEmpty("Password")) {
					OrderUtil.onLogStep(upreq, status, stamp, orderclean, payment, refid, callback);
					return;
				}

				String password = cinfo.getFieldAsString("Password");

				// TODO store address too
				AddUserRequest request = AddUserRequest.of(cinfo.getFieldAsString("Email"))
						.withFirstName(cinfo.getFieldAsString("FirstName"))
						.withLastName(cinfo.getFieldAsString("LastName"))
						.withEmail(cinfo.getFieldAsString("Email"))
						.withPhone(cinfo.getFieldAsString("Phone"))
						.withPassword(password);

				ServiceHub.call(request.toServiceRequest().withOutcome(new OperationOutcomeStruct() {
					@Override
					public void callback(Struct result) throws OperatingContextException {
						OperationContext.getOrThrow().touch();

						if (! this.hasErrors()) {
							String cid = Struct.objectToRecord(result).getFieldAsString("Id");

							orderclean.getFieldAsRecord("CustomerInfo").with("CustomerId", cid);
							upreq.withSetField("dcmCustomerInfo", orderclean.getFieldAsRecord("CustomerInfo"));

							upreq.withSetField("dcmCustomer", cid);

							ServiceHub.call(RequestFactory.signInRequest(cinfo.getFieldAsString("Email"), password, null)
									.toServiceRequest()
									.withOutcome(new OperationOutcomeStruct() {
										@Override
										public void callback(Struct result) throws OperatingContextException {
											Logger.info("Customer created from order: " + cid + " logged in: " + OperationContext.getOrThrow().getUserContext().getUsername());

											OrderUtil.onLogStep(upreq, status, stamp, orderclean, payment, refid, callback);
										}
									})
							);
						}
						else {
							OrderUtil.onLogStep(upreq, status, stamp, orderclean, payment, refid, callback);
						}
					}
				}));
			}
		}));
	}

	static public void onLogStep(DbRecordRequest upreq, String status, ZonedDateTime stamp, RecordStruct orderclean, Struct payment, String refid, OperationOutcomeStruct callback) throws OperatingContextException {
		//ZonedDateTime stamp = orderclean.getFieldAsDateTime("OrderDate");

		RecordStruct audit = RecordStruct.record()
				.with("Origin", "Customer")
				.with("Stamp", stamp)
				.with("Internal", false)
				.with("Comment", "Customer places order")
				.with("Status", status);

		upreq.withSetField("dcmAudit", TimeUtil.stampFmt.format(stamp), audit);


		ServiceHub.call(upreq.toServiceRequest().withOutcome(new OperationOutcomeStruct() {
			@Override
			public void callback(Struct result) throws OperatingContextException {
				OperationContext.getOrThrow().touch();

				// TODO mark coupons used if present
										/*
										<Field Name="dcmWasUsed" Type="Boolean" />
										<Field Name="dcmAmountUsed" Type="Decimal" />
										*/

				RecordStruct orderlog = RecordStruct.record()
						.with("Order", orderclean)
						.with("PaymentResponse", payment)
						.with("Log", OperationContext.getOrThrow().getController().getMessages());

				// TODO trigger Event to send emails

				// store in deposit file - TODO make sure this is not expanded locally, should be in encrypted deposit file only
				MemoryStoreFile msource = MemoryStoreFile.of(CommonPath.from("/" + refid + ".json"))
						.with(orderlog.toPrettyString());

				VaultUtil.transfer("StoreOrders", msource, CommonPath.from("/" + refid + ".json"), new OperationOutcomeStruct() {
					@Override
					public void callback(Struct result) throws OperatingContextException {
						//System.out.println(OperationContext.getOrThrow().toPrettyString());

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


						callback.returnResult();
					}
				});
			}
		}));
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
								VaultUtil.transfer("StoreOrders", msource, logpath, new OperationOutcomeStruct() {
									@Override
									public void callback(Struct result) throws OperatingContextException {
										//System.out.println(OperationContext.getOrThrow().toPrettyString());
										
										CommonPath imgpath = CommonPath.from("/" + id + "/ship-" + shipment.getFieldAsString("EntryId") + "-label.gif");
										
										MemoryStoreFile msource = MemoryStoreFile.of(imgpath)
												.with(new Memory(Base64.decodeFast(image)));
										
										VaultUtil.transfer("StoreOrders", msource, imgpath, new OperationOutcomeStruct() {
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

	static public void santitizeAndCalculateOrder(RecordStruct order, OperationOutcomeStruct callback) throws OperatingContextException {
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
		ListStruct pidlist = new ListStruct();
		//ListStruct remlist = new ListStruct();
		
		for (Struct itm : items.items())
			pidlist.with(((RecordStruct) itm).getFieldAsString("Product"));
		
		// grab weight / ship cost from here but don't store in order info
		// TODO consider dcmPurchaseOnline or dcmDisabled
		SelectDirectRequest req = SelectDirectRequest.of("dcmProduct")
			.withSelect(SelectFields.select()
				.with("Id", "Product")
				.with("dcmTitle", "Title")
				.with("dcmAlias", "Alias")
				.with("dcmSku", "Sku")
				.with("dcmDescription", "Description")
				.with("dcmInstructions", "Instructions")
				.with("dcmDelivery", "Delivery")
				.with("dcmImage", "Image")
				.with("dcmTag", "Tags")
				.with("dcmVariablePrice", "VariablePrice")
				.with("dcmMinimumPrice", "MinimumPrice")
				.with("dcmPrice", "Price")
				.with("dcmSalePrice", "SalePrice")
				.with("dcmTaxFree", "TaxFree")
				.with("dcmShipCost", "ShipCost")
				.with("dcmShipAmount", "ShipAmount")
				.with("dcmShipWeight", "ShipWeight")
				.withForeignField("dcmCategory", "CatShipAmount", "dcmShipAmount")
			)
			.withCollector(CollectorField.collect()
					.withField("Id")
					.withValues(pidlist)
			)
			.withWhere(WhereNotEqual.of("dcmDisabled", true));

		// do search
		ServiceHub.call(req.toServiceRequest().withOutcome(new OperationOutcomeStruct() {
			@Override
			public void callback(Struct result) throws OperatingContextException {
		    	OperationContext.getOrThrow().touch();
		    	
				if (this.hasErrors()) {
					callback.returnEmpty();
					return;
				}

				XElement sset = ApplicationHub.getCatalogSettings("CMS-Store");

				if (sset == null) {
					Logger.error("Missing store settings.");
					callback.returnEmpty();
					return;
				}

				AtomicReference<BigDecimal> itmcalc = new AtomicReference<>(BigDecimal.ZERO);
				AtomicReference<BigDecimal> taxcalc = new AtomicReference<>(BigDecimal.ZERO);
				//AtomicReference<BigDecimal> shipcalc = new AtomicReference<>(BigDecimal.ZERO);
				
				AtomicReference<BigDecimal> itemshipcalc = new AtomicReference<>(BigDecimal.ZERO);
				//AtomicReference<BigDecimal> catshipcalc = new AtomicReference<>(BigDecimal.ZERO);
				AtomicReference<BigDecimal> itemshipweight = new AtomicReference<>(BigDecimal.ZERO);
				
				// TODO look up shipping
				AtomicReference<BigDecimal> shipamt = new AtomicReference<>(BigDecimal.ZERO);
				
				// TODO look up coupons, check them and apply them
				AtomicReference<BigDecimal> itmdiscount = new AtomicReference<>(BigDecimal.ZERO);
				AtomicReference<BigDecimal> shipdiscount = new AtomicReference<>(BigDecimal.ZERO);
				
				// if we find items from submitted list in the database then add those items to our true item list
				ListStruct fnditems = new ListStruct();

				String orderdelivery = order.getFieldAsString("Delivery");

				// loop our items
				for (Struct itm : items.items()) {
					RecordStruct orgitem = (RecordStruct) itm;
				
					for (Struct match : ((ListStruct)result).items()) {
						RecordStruct rec = (RecordStruct) match;
						
						if (! rec.getFieldAsString("Product").equals(orgitem.getFieldAsString("Product")))
							continue;
					
						RecordStruct item = orgitem.deepCopy();
						
						// make sure we are using the 'real' values here, from the DB
						// we are excluding fields that are not valid in an order item, however these fields are still used in the calculation below
						item.copyFields(rec, "ShipAmount", "ShipWeight", "CatShipAmount");
						
						// add to the 'real' order list
						fnditems.with(item);
						
						BigDecimal price = item.isFieldEmpty("SalePrice") 
								? item.getFieldAsDecimal("Price", BigDecimal.ZERO) : item.getFieldAsDecimal("SalePrice");
								
						if (rec.getFieldAsBooleanOrFalse("VariablePrice")) {
							price = orgitem.getFieldAsDecimal("Price", BigDecimal.ZERO);
							
							BigDecimal min = rec.getFieldAsDecimal("MinimumPrice", BigDecimal.ZERO);
							
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

						if (! item.getFieldAsBooleanOrFalse("TaxFree"))
							taxcalc.set(taxcalc.get().add(total));

						String shipcost = item.getFieldAsString("ShipCost", "Regular");
						ListStruct delivery = item.getFieldAsList("Delivery");

						if ("Ship".equals(orderdelivery)) {
							for (int d = 0; d < delivery.getSize(); d++) {
								if (! "Ship".equals(delivery.getItemAsString(d)))
									continue;

								//if (! item.getFieldAsBooleanOrFalse("ShipFree"))
								//	shipcalc.set(shipcalc.get().add(total));

								if (rec.isNotFieldEmpty("ShipAmount")) {
									if ("Fixed".equals(shipcost))
										itemshipcalc.set(itemshipcalc.get().add(rec.getFieldAsDecimal("ShipAmount").multiply(qty)));
									else if ("Extra".equals(shipcost))    // TODO make it so Extra is in addition to normal shipping amt
										itemshipcalc.set(itemshipcalc.get().add(rec.getFieldAsDecimal("ShipAmount").multiply(qty)));
								}

								if (rec.isNotFieldEmpty("ShipWeight")) {
									// only include weight if not Free or Fixed
									if ("Regular".equals(shipcost) || "Extra".equals(shipcost))
										itemshipweight.set(itemshipweight.get().add(rec.getFieldAsDecimal("ShipWeight").multiply(qty)));
								}

								//if (rec.isNotFieldEmpty("CatShipAmount"))
								//	catshipcalc.set(catshipcalc.get().add(rec.getFieldAsDecimal("CatShipAmount").multiply(qty)));

								break;
							}
						}

						break;
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
				}

				// coupons

				CountDownCallback couponcd = new CountDownCallback(1, new OperationOutcomeEmpty() {
					@Override
					public void callback() {
						BigDecimal itmtotal = itmcalc.get().add(itmdiscount.get().negate());
						
						if (itmtotal.stripTrailingZeros().compareTo(BigDecimal.ZERO) < 0)
							itmtotal = BigDecimal.ZERO;
						
						BigDecimal shiptotal = shipamt.get().add(shipdiscount.get().negate());
						
						if (shiptotal.stripTrailingZeros().compareTo(BigDecimal.ZERO) < 0)
							shiptotal = BigDecimal.ZERO;
						
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
						
						callback.returnValue(order);
					}
				});

				/* TODO
				if (order.isFieldEmpty("CouponCode")) {
					couponcd.countDown();
					return;
				}
				*/

				// TODO add discount support
				couponcd.countDown();
				return;
				
				/*
				// grab weight / ship cost from here but don't store in order info
				SelectDirectRequest req2 = new SelectDirectRequest() 
					.withTable("dcmDiscount")
					.withSelect(new SelectFields()
						.withField("Id")
						.withField("dcmTitle", "Title")
						.withField("dcmCode", "Code")
						.withField("dcmMode", "Mode")
						.withField("dcmAmount", "Amount")
						.withField("dcmMinimumOrder", "MinimumOrder")			
						.withField("dcmStart", "Start")
						.withField("dcmExpire", "Expire")
						.withField("dcmOneTimeUse", "OneTimeUse")
						.withField("dcmWasUsed", "WasUsed")
					)
					.withCollector(new CollectorField("dcmCode").withValues(order.getFieldAsString("CouponCode")));

				// do search
				Hub.instance.getDatabase().submit(req2, new ObjectResult() {
					@Override
					public void process(CompositeStruct result) {
				    	OperationContext.get().touch();
				    	
						if (this.hasErrors()) {
							callback.complete();
							return;
						}
						
						if ((result == null) || (((ListStruct) result).getSize() == 0)) {
							couponcd.countDown();
							return;
						}
						
						/* TODO check all this and the order modes too
						 * 	calc off of order create date for star/expire 
						.withField("dcmMinimumOrder", "MinimumOrder")			
						.withField("dcmStart", "Start")
						.withField("dcmExpire", "Expire")
						* /
						
						RecordStruct crec = ((ListStruct) result).getItemAsRecord(0);
						
						if (crec.getFieldAsBooleanOrFalse("OneTimeUse") && crec.getFieldAsBooleanOrFalse("WasUsed")) {
							couponcd.countDown();
							return;
						}
						
						ListStruct disclist = new ListStruct();
						
						// TODO support other modes - FixedOffTotal,FixedOffProduct,PercentOffProduct,FixedOffShipping,PercentOffShipping,FlatShipping,FreeShipping
						
						String mode = crec.getFieldAsString("Mode");
						
						if ("FixedOffTotal".equals(mode)) {
							if (!crec.isFieldEmpty("Amount")) {
								itmdiscount.set(crec.getFieldAsDecimal("Amount"));		 // TODO off total not off prod
							}
						}

						disclist.addItem(new RecordStruct()
							// EntityId as uuid
							.withField("Discount", crec.getFieldAsString("Id"))
							.withField("Code", order.getFieldAsString("CouponCode"))
							.withField("Title", crec.getFieldAsString("Title"))
							.withField("Amount", itmdiscount.get())		// TODO or ship disc
						);
						
						order.setField("Discounts", disclist);
						
						couponcd.countDown();
					}
				});
		
				*/
			}
		}));
	}

}
