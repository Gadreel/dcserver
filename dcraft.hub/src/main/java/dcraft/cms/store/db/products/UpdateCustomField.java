package dcraft.cms.store.db.products;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.request.update.UpdateRecordRequest;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;

public class UpdateCustomField implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();
		String id = data.getFieldAsString("Id");

		TablesAdapter db = TablesAdapter.of(request);

		DbRecordRequest req = UpdateRecordRequest.update()
				.withId(id)
				.withTable("dcmProductCustomFields")
				.withConditionallyUpdateFields(data, "Position", "dcmPosition",
						"FieldType", "dcmFieldType", "DataType", "dcmDataType", "Label", "dcmLabel", "LongLabel", "dcmLongLabel",
						"Placeholder", "dcmPlaceholder", "Pattern", "dcmPattern", "Required", "dcmRequired",
						"MaxLength", "dcmMaxLength", "Horizontal", "dcmHorizontal", "Price", "dcmPrice"
				);

		ListStruct roptions = data.getFieldAsList("RemoveOptions");
		
		if (roptions != null) {
			for (int i = 0; i < roptions.size(); i++) {
				String optid = roptions.getItemAsString(i);
				
				req.withRetireField("dcmOptionLabel", optid);
				req.withRetireField("dcmOptionValue", optid);
				req.withRetireField("dcmOptionPrice", optid);
			}
		}
		
		ListStruct options = data.getFieldAsList("SetOptions");

		if (options != null) {
			//List<String> keys = db.getStaticListKeys("dcmProductCustomFields", id, "dcmOptionLabel");
			//int last = (int) StringUtil.parseInt(keys.get(keys.size() - 1), 0);
			
			for (int i = 0; i < options.size(); i++) {
				RecordStruct option = options.getItemAsRecord(i);

				String optid = option.getFieldAsString("Id");

				/*
				String optid = option.isNotFieldEmpty("Id")
						? option.getFieldAsString("Id")
						: StringUtil.leftPad(last + "", 4, '0');;
						
				last++;
				*/

				req.withUpdateField("dcmOptionLabel", optid, option.getFieldAsString("Label"));
				req.withUpdateField("dcmOptionValue", optid, option.getFieldAsString("Value"));
				req.withUpdateField("dcmOptionPrice", optid, option.getFieldAsString("Price"));
				req.withUpdateField("dcmOptionWeight", optid, option.getFieldAsString("Weight"));
				req.withUpdateField("dcmOptionDisabled", optid, option.getFieldAsString("Disabled"));
			}
		}

		TableUtil.updateRecord(db, req);

		callback.returnEmpty();
	}
}
