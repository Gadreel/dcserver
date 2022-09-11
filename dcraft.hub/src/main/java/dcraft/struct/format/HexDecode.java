package dcraft.struct.format;

import dcraft.struct.Struct;
import dcraft.util.Base64;
import dcraft.util.HexUtil;
import dcraft.util.StringUtil;

import java.nio.charset.StandardCharsets;

// if string starts with a number, pad it out with zeros
public class HexDecode implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		// TODO act on a list as well as on a scalar

		String val = Struct.objectToString(value);

		if (StringUtil.isNotEmpty(val)) {
			// for now String mode turn B64 string into normal string
			value = new String(HexUtil.decodeHex(val), StandardCharsets.UTF_8);
		}

		return FormatResult.result(value);
	}
}
