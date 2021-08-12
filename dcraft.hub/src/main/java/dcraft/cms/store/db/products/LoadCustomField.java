package dcraft.cms.store.db.products;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.request.query.SelectFields;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.RecordStruct;

public class LoadCustomField implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();
		
		String id = data.getFieldAsString("Id");

		TablesAdapter db = TablesAdapter.of(request);
		
		SelectFields flds = SelectFields.select()
				.with("Id")
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
						.withAs("Weight","dcmOptionWeight")
						.with("dcmOptionDisabled", "Disabled")
				);

		callback.returnValue(TableUtil.getRecord(db, OperationContext.getOrThrow(), "dcmProductCustomFields", id, flds));
	}
}
