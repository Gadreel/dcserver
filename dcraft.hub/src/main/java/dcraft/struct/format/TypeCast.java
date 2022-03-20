package dcraft.struct.format;

import dcraft.hub.ResourceHub;
import dcraft.schema.DataType;
import dcraft.struct.BaseStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.ScalarStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;

public class TypeCast implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		DataType dataType = ResourceHub.getResources().getSchema().getType(format);

		if ((dataType == null) || (dataType.getKind() != DataType.DataKind.Scalar))
			return FormatResult.result(value);

		if (value instanceof ListStruct) {
			ListStruct list = (ListStruct) value;
			ListStruct resp = ListStruct.list();

			for (int i = 0; i < list.size(); i++) {
				BaseStruct val = list.getItem(i);

				ScalarStruct newval = (ScalarStruct) dataType.create();
				newval.adaptValue(val);
				resp.with(newval);
			}
			
			return FormatResult.result(resp);
		}

		ScalarStruct newval = (ScalarStruct) dataType.create();
		newval.adaptValue(value);

		return FormatResult.result(newval);
	}
}
