package dcraft.cms.store.db.comp;

import dcraft.db.proc.IComposer;
import dcraft.db.request.query.SelectFields;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.struct.*;
import dcraft.struct.builder.BuilderStateException;
import dcraft.struct.builder.ICompositeBuilder;
import dcraft.struct.scalar.BooleanStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.util.StringUtil;

import java.math.BigDecimal;
import java.text.DecimalFormat;

public class OrderItemCustomFields implements IComposer {
	@Override
	public void writeField(ICompositeBuilder out, TablesAdapter db, IVariableAware scope, String table, String id,
						   RecordStruct field, boolean compact) throws OperatingContextException
	{
		try {
			out.startList();

			RecordStruct rec = TableUtil.getRecord(db, OperationContext.getOrThrow(), table, id, SelectFields.select()
					.with("dcmOrderDate", "OrderDate")
					.with("dcmStatus", "Status")
					.withGroup("dcmItemEntryId", "Items", "EntryId", SelectFields.select()
							.with("dcmItemProduct", "Product")
							.with("dcmItemQuantity", "Quantity")
							.with("dcmItemPrice", "Price")
							.with("dcmItemCustomFields", "CustomFields")
							.withSubquery("dcmItemProduct", "ProductInfo", SelectFields.select()
									.with("dcmTitle", "Title")
									.with("dcmAlias", "Alias")
									.with("dcmSku", "Sku")
									.withReverseSubquery("Fields",	"dcmProductCustomFields", "dcmProduct",	SelectFields.select().with("Id")
											.withAs("Position", "dcmPosition")
											.withAs("FieldType","dcmFieldType")
											.withAs("DataType", "dcmDataType")
											.withAs("Label", "dcmLabel")
											.withAs("Price","dcmPrice")
											.withGroup("dcmOptionLabel", "Options", "Id", SelectFields.select()
													.withAs("Label","dcmOptionLabel")
													.withAs("Value","dcmOptionValue")
													.withAs("Price","dcmOptionPrice")
													.withAs("Weight","dcmOptionWeight")
													.with("dcmOptionDisabled", "Disabled")
											)
									)
							)
					)
			);

			if (rec != null) {
				ListStruct items = rec.getFieldAsList("Items");

				for (int i = 0; i < items.size(); i++) {
					RecordStruct item = items.getItemAsRecord(i);

					RecordStruct pinfo = item.getFieldAsRecord("ProductInfo");

					out.startRecord();
					out.field("EntryId", item.getField("EntryId"));
					out.field("Options");
					out.startList();

					if (item.isNotFieldEmpty("CustomFields")) {
						RecordStruct customs = item.getFieldAsRecord("CustomFields");
						ListStruct fields = pinfo.getFieldAsList("Fields");

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
											BaseStruct value = custom.getValue();

											if ((value != null) && value.equals(opt.getField("Value"))) {
												out.startRecord();
												out.field("Id", fld.getFieldAsString("Id"));
												out.field("Label", fld.getFieldAsString("Label"));
												out.field("DisplayValue", opt.getFieldAsString("Label"));
												out.field("Price", opt.getFieldAsDecimal("Price", BigDecimal.ZERO));
												out.field("Value", opt.getField("Value"));
												out.endRecord();

												break;
											}
										}
									}
									else {
										boolean pass = ((custom.getValue() instanceof BooleanStruct) && Struct.objectToBooleanOrFalse(custom.getValue()))
												|| ((custom.getValue() instanceof StringStruct) && StringUtil.isNotEmpty(Struct.objectToString(custom.getValue())));

										if (pass) {
											BaseStruct value = custom.getValue();
											String display = Struct.objectToString(value);

											if (value instanceof BooleanStruct) {
												display = Struct.objectToBooleanOrFalse(value) ? "yes" : "no";
											}

											out.startRecord();
											out.field("Id", fld.getFieldAsString("Id"));
											out.field("Label", fld.getFieldAsString("Label"));
											out.field("DisplayValue", display);
											out.field("Price", fld.getFieldAsDecimal("Price", BigDecimal.ZERO));
											out.field("Value", value);
											out.endRecord();
										}
									}
								}
							}
						}
					}

					out.endList();
					out.endRecord();
				}
			}

			out.endList();
		}
		catch (BuilderStateException x) {
			// TODO Auto-generated catch block
			x.printStackTrace();
		}
	}
}
