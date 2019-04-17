package dcraft.struct.format;

import dcraft.struct.Struct;
import dcraft.util.StringUtil;

public class TrueFalseFormatter implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		Boolean val = Struct.objectToBoolean(value);
		
		if (val == null)
			val = Boolean.FALSE;
		
		String fmt = "True,False";
		
		if (StringUtil.isNotEmpty(format) && format.contains(","))
			fmt = format;

		int pos = fmt.indexOf(',');

		value = val ? fmt.substring(0, pos) : fmt.substring(pos + 1);
		
		return FormatResult.result(value);
	}
}
