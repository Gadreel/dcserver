package dcraft.struct.format;

import dcraft.mail.MailUtil;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;

public class CleanEmailAddress implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		// TODO act on a list as well as on a scalar

		String val = Struct.objectToString(value);

		if (StringUtil.isNotEmpty(val)) {
			boolean index = StringUtil.isNotEmpty(format) ? format.contains("index") : false;

			value = index ? MailUtil.indexableEmailAddress(val) : MailUtil.normalizeEmailAddress(val);
		}
		
		return FormatResult.result(value);
	}
}
