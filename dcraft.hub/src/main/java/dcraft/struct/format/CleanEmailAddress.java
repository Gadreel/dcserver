package dcraft.struct.format;

import dcraft.struct.Struct;
import dcraft.util.IOUtil;
import dcraft.util.MailUtil;
import dcraft.util.StringUtil;

public class CleanEmailAddress implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		// TODO act on a list as well as on a scalar

		String val = Struct.objectToString(value);

		if (StringUtil.isNotEmpty(val)) {
			value = MailUtil.cleanEmailDomainName(val);
		}
		
		return FormatResult.result(value);
	}
}
