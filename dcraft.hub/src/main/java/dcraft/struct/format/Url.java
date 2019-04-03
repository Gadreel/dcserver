package dcraft.struct.format;

import dcraft.struct.Struct;
import dcraft.util.PeopleUtil;
import dcraft.util.StringUtil;

public class Url implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		String val = Struct.objectToString(value);

		if (StringUtil.isNotEmpty(val)) {
			// TODO only format right now is "domain", support others
			if (StringUtil.isNotEmpty(format) && format.equals("domain")) {
				int pos = val.indexOf("://");

				if (pos != -1)
					val = val.substring(pos + 3);

				pos = val.indexOf("/");

				if (pos != -1)
					val = val.substring(0, pos);

				value = val;
			}
			else if ("http".equals(format)) {
				if (! val.startsWith("https://") && ! val.startsWith("http://")) {
					value = "http://" + val;		// don't default to https
				}
			}
		}
		
		return FormatResult.result(value);
	}
}
