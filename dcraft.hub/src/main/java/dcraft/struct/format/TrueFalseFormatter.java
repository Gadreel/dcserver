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
			
		String[] split = fmt.split(",");
		
		value = val ? split[0] : split[1];
		
		return FormatResult.result(value);
	}
}
