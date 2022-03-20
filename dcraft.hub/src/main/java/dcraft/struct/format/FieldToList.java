package dcraft.struct.format;

import dcraft.struct.ListStruct;
import dcraft.util.StringUtil;

public class FieldToList implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		if (StringUtil.isNotEmpty(format) && (value instanceof ListStruct)) {
			value = ((ListStruct) value).fieldToList(format);
		}
		
		return FormatResult.result(value);
	}
}
