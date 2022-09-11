package dcraft.struct.format;

import dcraft.struct.Struct;
import dcraft.util.Base64;
import dcraft.util.HexUtil;
import dcraft.util.StringUtil;

import java.nio.charset.StandardCharsets;

// if string starts with a number, pad it out with zeros
public class HexEncode implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		// TODO act on a list as well as on a scalar

		// for now String mode turn regular string into B64 string
		String val = Struct.objectToString(value);

		if (StringUtil.isNotEmpty(val)) {
			value = HexUtil.encodeHex(val);
		}

		return FormatResult.result(value);
	}
}
