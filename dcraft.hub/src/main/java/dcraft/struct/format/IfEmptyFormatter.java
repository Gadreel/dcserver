package dcraft.struct.format;

import dcraft.struct.BaseStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;

public class IfEmptyFormatter implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		boolean content = false;
		
		if (value instanceof BaseStruct) {
			content = ! ((BaseStruct) value).isEmpty();
		}
		else if (value instanceof CharSequence) {
			content = StringUtil.isNotEmpty((CharSequence)value);
		}
		else {
			content = (value != null);
		}
		
		if (! content) {
			value = format;
		}
		
		return FormatResult.result(value);
	}
}
