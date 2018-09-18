package dcraft.struct.format;

import dcraft.struct.Struct;
import dcraft.util.StringUtil;

public class ToStringFormatter implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		String val = Struct.objectToString(value);
		
		return FormatResult.result(val);
	}
}
