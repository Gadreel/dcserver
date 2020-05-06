package dcraft.cms.store.db.products;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.proc.filter.CurrentRecord;
import dcraft.db.proc.filter.Unique;
import dcraft.db.request.query.SelectFields;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.util.StringUtil;

public class LoadCustomFields implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();
		
		String prodid = data.getFieldAsString("Product");

		TablesAdapter db = TablesAdapter.ofNow(request);
		
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
						.with("dcmOptionDisabled", "Disabled")
				);
		
		Unique collector = Unique.unique();

		if (StringUtil.isNotEmpty(prodid)) {
			db.traverseIndex(OperationContext.getOrThrow(), "dcmProductCustomFields", "dcmProduct", prodid, collector.withNested(CurrentRecord.current()));
		}
		else {
			String formid = data.getFieldAsString("BasicForm");

			db.traverseIndex(OperationContext.getOrThrow(), "dcmProductCustomFields", "dcmBasicCustomForm", formid, collector.withNested(CurrentRecord.current()));
		}

		ListStruct result = ListStruct.list();

		for (Object fid : collector.getValues()) {
			result.with(TableUtil.getRecord(db, OperationContext.getOrThrow(), "dcmProductCustomFields", fid.toString(), flds));
		}

		result.sortRecords("Position", false);

		callback.returnValue(result);
	}
}
