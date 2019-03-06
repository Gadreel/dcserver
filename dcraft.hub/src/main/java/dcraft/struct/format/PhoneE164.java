package dcraft.struct.format;

import dcraft.struct.Struct;
import dcraft.util.PeopleUtil;
import dcraft.util.StringUtil;

public class PhoneE164 implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		String val = Struct.objectToString(value);

		if (StringUtil.isNotEmpty(val)) {
			value = PeopleUtil.formatPhone(val);
		}
		
		return FormatResult.result(value);
	}
}
