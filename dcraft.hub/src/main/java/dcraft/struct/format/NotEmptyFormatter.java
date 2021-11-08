package dcraft.struct.format;

import dcraft.struct.BaseStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;

public class NotEmptyFormatter implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		if (value instanceof BaseStruct) {
			value = ! ((BaseStruct) value).isEmpty();
		}
		else if (value instanceof CharSequence) {
			value = StringUtil.isNotEmpty((CharSequence)value);
		}
		else {
			value = (value != null);
		}
		
		return FormatResult.result(value);
	}
}
