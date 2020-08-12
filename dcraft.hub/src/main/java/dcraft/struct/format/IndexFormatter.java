package dcraft.struct.format;

import dcraft.struct.ListStruct;
import dcraft.util.StringUtil;

public class IndexFormatter implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		if (value instanceof ListStruct) {
			int idx = Integer.valueOf(StringUtil.isNotEmpty(format) ? format : "0");

			value = ((ListStruct) value).getItem(idx);
		}
		
		return FormatResult.result(value);
	}
}
