package dcraft.struct.format;

import dcraft.struct.Struct;
import dcraft.util.StringUtil;
import dcraft.util.json3.JsonStringEncoder;
import dcraft.util.json3.Util;

public class DecodeJsonFormatter implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		String val = Struct.objectToString(value);
		
		if (val == null)
			return FormatResult.result(val);
		
		value = Util.decodeJsonString(val);
		
		return FormatResult.result(value);
	}
}
