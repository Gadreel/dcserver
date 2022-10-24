package dcraft.struct.format;

import dcraft.struct.ListStruct;
import dcraft.struct.Struct;
import dcraft.util.Base64;
import dcraft.util.StringUtil;

import java.nio.charset.StandardCharsets;

// if string starts with a number, pad it out with zeros
public class Base64Decode implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		// TODO act on a list as well as on a scalar

		if (value instanceof ListStruct) {
			ListStruct list = (ListStruct) value;
			ListStruct newlist = ListStruct.list();

			for (int i = 0; i < list.size(); i++) {
				String val = list.getItemAsString(i);

				if (StringUtil.isNotEmpty(val)) {
					// for now String mode turn B64 string into normal string
					val = Base64.decodeToUtf8(val);
				}

				newlist.with(val);
			}

			return FormatResult.result(newlist);
		}
		else {
			String val = Struct.objectToString(value);

			if (StringUtil.isNotEmpty(val)) {
				// for now String mode turn B64 string into normal string
				value = Base64.decodeToUtf8(val);
			}
		}

		return FormatResult.result(value);
	}
}
