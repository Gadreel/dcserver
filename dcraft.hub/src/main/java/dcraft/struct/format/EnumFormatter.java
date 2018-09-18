package dcraft.struct.format;

import dcraft.hub.ResourceHub;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;

public class EnumFormatter implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		String val = Struct.objectToString(value);
		
		if ((val == null) || StringUtil.isEmpty(format)) {
			value = null;
		}
		else {
			value = ResourceHub.getResources().getLocale().tr("_enum_" + format + "_" + val);
		}
		
		return FormatResult.result(value);
	}
}
