package dcraft.struct.format;

import dcraft.struct.Struct;
import dcraft.util.StringUtil;

// if string starts with a number, pad it out with zeros
public class NumberPad implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		String val = Struct.objectToString(value);
		
		if (StringUtil.isNotEmpty(format)) {
			if (StringUtil.isNotEmpty(val)) {
				int period = -1;

				for (int i = 0; i < val.length(); i++) {
					char ch = val.charAt(i);

					if (ch == '.') {
						period = i;
					}

					if (! Character.isDigit(ch) && (ch != '.')) {
						// does not start with number
						if (i == 0)
							break;

						int cpos = format.indexOf(',');
						String leftpad = (cpos == -1) ? format : format.substring(0, cpos);
						String rightpad = (cpos == -1) ? "0" : format.substring(cpos + 1);

						int lpad = (int) StringUtil.parseInt(leftpad, 0);
						int rpad = (int) StringUtil.parseInt(rightpad, 0);

						String left = (period == -1) ? (period == 0) ? "" : val.substring(0, i) : val.substring(0, period);
						String right = (period == -1) ? "" : val.substring(period + 1, i);

						left = StringUtil.leftPad(left, lpad, '0');
						right = StringUtil.rightPad(right, rpad, '0');

						value = left;

						if (rpad > 0)
							value += "." + right;

						value += val.substring(i);

						//value = StringUtil.leftPad(val, (lpad - i) + val.length(), '0');

						break;
					}
				}
			}
		}

		System.out.println(value);

		return FormatResult.result(value);
	}
}
