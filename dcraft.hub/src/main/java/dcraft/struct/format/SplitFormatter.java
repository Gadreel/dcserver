package dcraft.struct.format;

import dcraft.struct.ListStruct;
import dcraft.struct.Struct;
import dcraft.util.PeopleUtil;
import dcraft.util.StringUtil;

public class SplitFormatter implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		String val = Struct.objectToString(value);

		if (StringUtil.isNotEmpty(val)) {
			String regex = StringUtil.isNotEmpty(format) ? format : ",";

			value = ListStruct.list(val.split(regex));
		}

		return FormatResult.result(value);
	}
}
