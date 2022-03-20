package dcraft.struct.format;

import dcraft.struct.Struct;
import dcraft.util.StringUtil;

// if string starts with a number, pad it out with zeros
public class Lower implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		// TODO act on a list as well as on a scalar

		String val = Struct.objectToString(value);

		if (StringUtil.isNotEmpty(val)) {
			value = val.toLowerCase();
		}

		return FormatResult.result(value);
	}
}
