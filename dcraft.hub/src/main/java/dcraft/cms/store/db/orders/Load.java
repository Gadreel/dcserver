package dcraft.cms.store.db.orders;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.request.query.LoadRecordRequest;
import dcraft.db.request.query.SelectFields;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.service.ServiceHub;
import dcraft.struct.FieldStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.BooleanStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.util.StringUtil;

import java.math.BigDecimal;

public class Load implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		TablesAdapter db = TablesAdapter.ofNow(request);

		RecordStruct rec = TableUtil.getRecord(db, OperationContext.getOrThrow(), "dcmOrder", data.getFieldAsString("Id"), SelectFields.select()
				.with("dcmOrderDate", "OrderDate")
				.with("dcmStatus", "Status")
				.with("dcmLastStatusDate", "LastStatusDate")
				.with("dcmCustomer", "Customer")
				.with("dcmCustomerInfo", "CustomerInfo")
				.with("dcmDelivery", "Delivery")
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
				.with("dcmShipmentInfo", "Shipments")
				.with("dcmExtra", "Extra")
				.withGroup("dcmItemEntryId", "Items", "EntryId", SelectFields.select()
						.with("dcmItemProduct", "Product")
						.with("dcmItemQuantity", "Quantity")
						.with("dcmItemPrice", "Price")
						.with("dcmItemTotal", "Total")
						.with("dcmItemStatus", "Status")
						.with("dcmItemUpdated", "Updated")
						.with("dcmItemShipment", "Shipment")
						.with("dcmItemCustomFields", "CustomFields")
						.withSubquery("dcmItemProduct", "ProductInfo", SelectFields.select()
								.with("dcmTitle", "Title")
								.with("dcmAlias", "Alias")
								.with("dcmSku", "Sku")
								.with("dcmDescription", "Description")
								.with("dcmInstructions", "Instructions")
								.with("dcmImage", "Image")
								.withReverseSubquery("Fields",	"dcmProductCustomFields", "dcmProduct",	SelectFields.select().with("Id")
									.withAs("Position", "dcmPosition")
									.withAs("FieldType","dcmFieldType")
									.withAs("DataType", "dcmDataType")
									.withAs("Label", "dcmLabel")
									.withAs("LongLabel", "dcmLongLabel")
									.withAs("Placeholder","dcmPlaceholder")
									.withAs("Pattern","dcmPattern")
									.withAs("Required","dcmRequired")
									.withAs("MaxLength","dcmMaxLength")
									.withAs("Horizontal","dcmHorizontal")
									.withAs("Price","dcmPrice")
									.withGroup("dcmOptionLabel", "Options", "Id", SelectFields.select()
											.withAs("Label","dcmOptionLabel")
											.withAs("Value","dcmOptionValue")
											.withAs("Price","dcmOptionPrice")
											.with("dcmOptionDisabled", "Disabled")
									)
							)
						)
				)
		);

		if (rec == null) {
			Logger.error("Order not found");
			callback.returnEmpty();
			return;
		}

		// Alias, Image, Instructions, Title, Sku, Description - Price, Quantity, Total

		ListStruct items = rec.getFieldAsList("Items");

		for (int i = 0; i < items.size(); i++) {
			RecordStruct item = items.getItemAsRecord(i);

			RecordStruct pinfo = item.getFieldAsRecord("ProductInfo");
			item.removeField("ProductInfo");

			item
					.with("Title", pinfo.getField("Title"))
					.with("Alias", pinfo.getField("Alias"))
					.with("Sku", pinfo.getField("Sku"))
					.with("Description", pinfo.getField("Description"))
					.with("Instructions", pinfo.getField("Instructions"))
					.with("Image", pinfo.getField("Image"));

			if (item.isNotFieldEmpty("CustomFields")) {
				RecordStruct customs = item.getFieldAsRecord("CustomFields");
				ListStruct fields = pinfo.getFieldAsList("Fields");
				ListStruct formattedcustoms = ListStruct.list();

				// sort and loop fields to keep stuff in proper order
				fields.sortRecords("Position", false);

				for (int fd = 0; fd < fields.size(); fd++) {
					RecordStruct fld = fields.getItemAsRecord(fd);

					for (FieldStruct custom : customs.getFields()) {
						if (custom.getName().equals(fld.getFieldAsString("Id"))) {
							if (fld.isNotFieldEmpty("Options")) {
								ListStruct options = fld.getFieldAsList("Options");

								for (int n = 0; n < options.size(); n++) {
									RecordStruct opt = options.getItemAsRecord(n);

									if (custom.getValue().equals(opt.getField("Value"))) {
										formattedcustoms.with(
												RecordStruct.record()
														.with("Id", fld.getFieldAsString("Id"))
														.with("Label", fld.getFieldAsString("Label"))
														.with("DisplayValue", opt.getFieldAsString("Label"))
														.with("Price", opt.getFieldAsDecimal("Price", BigDecimal.ZERO))
														.with("Value", opt.getField("Value"))
										);

										break;
									}
								}
							}
							else {
								boolean pass = ((custom.getValue() instanceof BooleanStruct) && Struct.objectToBooleanOrFalse(custom.getValue()))
										|| ((custom.getValue() instanceof StringStruct) && StringUtil.isNotEmpty(Struct.objectToString(custom.getValue())));

								if (pass) {
									Struct value = custom.getValue();
									String display = Struct.objectToString(value);

									if (value instanceof BooleanStruct) {
										display = Struct.objectToBooleanOrFalse(value) ? "yes" : "no";
									}

									formattedcustoms.with(
											RecordStruct.record()
													.with("Id", fld.getFieldAsString("Id"))
													.with("Label", fld.getFieldAsString("Label"))
													.with("DisplayValue", display)
													.with("Price", fld.getFieldAsDecimal("Price", BigDecimal.ZERO))
													.with("Value", value)
									);
								}
							}
						}
					}
				}

				// replace
				item.with("CustomFields", formattedcustoms);
			}


		}

		callback.returnValue(rec);
	}
}
