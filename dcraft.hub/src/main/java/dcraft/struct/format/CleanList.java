package dcraft.struct.format;

import dcraft.struct.BaseStruct;
import dcraft.struct.ListStruct;

public class CleanList implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		if (value instanceof ListStruct) {
			ListStruct list = (ListStruct) value;
			ListStruct resp = ListStruct.list();

			for (int i = 0; i < list.size(); i++) {
				BaseStruct val = list.getItem(i);

				if ((val != null) && ! val.isEmpty())
					resp.with(val);
				else
					resp.with(format);		// default value
			}
			
			return FormatResult.result(resp);
		}

		return FormatResult.result(value);
	}
}
