package dcraft.struct.format;

import dcraft.struct.Struct;
import dcraft.util.StringUtil;

public class SubstringFormatter implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		String val = Struct.objectToString(value);
		
		if ((val == null) || StringUtil.isEmpty(format))
			return FormatResult.result(val);

		int npos = format.indexOf(':');
		
		if ((npos == -1) || (npos == format.length())) {
			int p = Integer.valueOf(format);
			
			value = (p >= val.length()) ? null : val.substring(p);
		}
		else {
			int p1 = Integer.valueOf(format.substring(0, npos));
			int p2 = Integer.valueOf(format.substring(npos + 1));
			
			if (p1 < 0)
				p1 = 0;
			
			if (p2 < p1)
				p2 = p1;
			
			if (p1 > val.length()) {
				return FormatResult.result(null);
			}
			else if (p2 > val.length()) {
				p2 = val.length();
			}
			
			value = val.substring(p1, p2);
		}
		
		return FormatResult.result(value);
	}
}
