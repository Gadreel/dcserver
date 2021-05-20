package dcraft.struct.format;

import dcraft.struct.Struct;
import dcraft.util.StringUtil;

// if string starts with a number, pad it out with zeros
public class Upper implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		String val = Struct.objectToString(value);

		if (StringUtil.isNotEmpty(val)) {
			value = val.toUpperCase();
		}

		return FormatResult.result(value);
	}
}
