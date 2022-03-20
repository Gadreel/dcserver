package dcraft.struct.format;

import dcraft.struct.BaseStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.util.StringUtil;

public class Slice implements IFormatter {

	@Override
	public FormatResult format(Object value, String op, String format) {
		if ((value instanceof ListStruct) && StringUtil.isNotEmpty(format)) {
			ListStruct list = (ListStruct) value;
			ListStruct result = ListStruct.list();

			int start = 0;
			int end = list.getSize();
			int pos = format.indexOf(',');

			if (pos == -1) {
				start = (int) StringUtil.parseInt(format, 0);
			}
			else {
				start = (int) StringUtil.parseInt(format.substring(0, pos), start);
				end = (int) StringUtil.parseInt(format.substring(pos + 1), end);
			}

			for (int i = 0; i < list.size(); i++) {
				if ((i < start) || (i > end))
					result.with(list.getItem(i));
			}

			return FormatResult.result(result);
		}

		return FormatResult.result(value);
	}
}
