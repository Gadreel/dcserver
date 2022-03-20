package dcraft.struct.format;

import dcraft.struct.Struct;
import dcraft.util.Base64;
import dcraft.util.StringUtil;

import java.nio.charset.StandardCharsets;

// if string starts with a number, pad it out with zeros
public class Base64Encode implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		// TODO act on a list as well as on a scalar

		// for now String mode turn regular string into B64 string
		String val = Struct.objectToString(value);

		if (StringUtil.isNotEmpty(val)) {
			value = Base64.encodeToString(val.getBytes(StandardCharsets.UTF_8), false);
		}

		return FormatResult.result(value);
	}
}
