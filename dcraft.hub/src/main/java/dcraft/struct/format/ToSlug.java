package dcraft.struct.format;

import dcraft.struct.BaseStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.util.StringUtil;

public class ToSlug implements IFormatter {
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

		value = ToSlug.format(value);

		return FormatResult.result(value);
	}

	static public Object format(Object value) {
		if (value != null) {
			if ((value instanceof StringStruct) && ! ((StringStruct) value).isEmpty()) {
				((StringStruct) value).adaptValue(StringUtil.slug(value.toString()));
			}
			else if (value instanceof CharSequence) {
				CharSequence val = (CharSequence) value;

				if (StringUtil.isNotEmpty(val)) {
					value = StringUtil.slug(val.toString());
				}
			}
		}

		return value;
	}
}
