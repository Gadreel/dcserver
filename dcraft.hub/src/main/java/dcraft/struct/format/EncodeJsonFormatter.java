package dcraft.struct.format;

import dcraft.struct.Struct;
import dcraft.util.json3.Util;

public class EncodeJsonFormatter implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		String val = Struct.objectToString(value);
		
		if (val == null)
			return FormatResult.result(val);
		
		value = Util.encodeJsonString(val);
		
		return FormatResult.result(value);
	}
}
