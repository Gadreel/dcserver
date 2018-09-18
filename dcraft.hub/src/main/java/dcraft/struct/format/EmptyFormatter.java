package dcraft.struct.format;

import dcraft.struct.Struct;
import dcraft.util.StringUtil;

public class EmptyFormatter implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		if (value instanceof Struct) {
			value = ((Struct) value).isEmpty();
		}
		else if (value instanceof CharSequence) {
			value = StringUtil.isEmpty((CharSequence)value);
		}
		else {
			value = (value == null);
		}
		
		return FormatResult.result(value);
	}
}
