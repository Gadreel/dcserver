package dcraft.struct.format;

import dcraft.struct.BaseStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.util.StringUtil;

public class Trim implements IFormatter {
	// only trim strings, ignore all other types

	@Override
	public FormatResult format(Object value, String op, String format) {
		if (value instanceof ListStruct) {
			ListStruct list = (ListStruct) value;

			for (int i = 0; i <list.size(); i++) {
				BaseStruct val = list.getItem(i);

				if ((val != null) && ! val.isEmpty() && (val instanceof StringStruct)) {
					((StringStruct) val).adaptValue(val.toString().trim());
				}
			}

			return FormatResult.result(list);
		}

		value = Trim.format(value);

		return FormatResult.result(value);
	}

	static public Object format(Object value) {
		if (value != null) {
			if ((value instanceof StringStruct) && ! ((StringStruct) value).isEmpty()) {
				((StringStruct) value).adaptValue(value.toString().trim());
			}
			else if (value instanceof CharSequence) {
				CharSequence val = (CharSequence) value;

				if (StringUtil.isNotEmpty(val)) {
					value = val.toString().trim();
				}
			}
		}

		return value;
	}
}
