package dcraft.cms.store.db.products;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.request.update.InsertRecordRequest;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.util.RndUtil;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;

public class AddCustomField implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		TablesAdapter db = TablesAdapter.ofNow(request);

		DbRecordRequest req = InsertRecordRequest.insert()
				.withTable("dcmProductCustomFields")
				.withConditionallyUpdateFields(data, "Product", "dcmProduct", "BasicForm", "dcmBasicCustomForm", "Position", "dcmPosition",
						"FieldType", "dcmFieldType", "DataType", "dcmDataType", "Label", "dcmLabel", "LongLabel", "dcmLongLabel",
						"Placeholder", "dcmPlaceholder", "Pattern", "dcmPattern", "Required", "dcmRequired",
						"MaxLength", "dcmMaxLength", "Horizontal", "dcmHorizontal", "Price", "dcmPrice"
				);

		ListStruct options = data.getFieldAsList("AddOptions");

		if (options != null) {
			for (int i = 0; i < options.size(); i++) {
				RecordStruct option = options.getItemAsRecord(i);

				String optid = StringUtil.leftPad(i + "", 4, '0');;

				req.withUpdateField("dcmOptionLabel", optid, option.getFieldAsString("Label"));
				req.withUpdateField("dcmOptionValue", optid, option.getFieldAsString("Value"));
				req.withUpdateField("dcmOptionPrice", optid, option.getFieldAsString("Price"));
			}
		}

		String newid = TableUtil.updateRecord(db, req);

		callback.returnValue(
			RecordStruct.record()
				.with("Id", newid)
		);
	}
}
