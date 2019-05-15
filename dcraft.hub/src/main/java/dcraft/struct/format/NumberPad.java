package dcraft.struct.format;

import dcraft.struct.Struct;
import dcraft.util.StringUtil;

// if string starts with a number, pad it out with zeros
public class NumberPad implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		String val = Struct.objectToString(value);
		
		if (StringUtil.isNotEmpty(val)) {
			for (int i = 0; i < val.length(); i++) {
				char ch = val.charAt(i);
				
				if (! Character.isDigit(ch)) {
					// does not start with number
					if (i == 0)
						break;
					
					int pad = (int) StringUtil.parseInt(format, 0);
					
					value = StringUtil.leftPad(val, (pad - i) + val.length(), '0');
					
					break;
				}
			}
		}
		
		return FormatResult.result(value);
	}
}
