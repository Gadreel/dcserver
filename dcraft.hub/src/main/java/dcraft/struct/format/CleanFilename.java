package dcraft.struct.format;

import dcraft.struct.Struct;
import dcraft.util.IOUtil;
import dcraft.util.PeopleUtil;
import dcraft.util.StringUtil;

public class CleanFilename implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		// TODO act on a list as well as on a scalar

		String val = Struct.objectToString(value);

		if (StringUtil.isNotEmpty(val)) {
			value = IOUtil.toCleanFilename(val);
		}
		
		return FormatResult.result(value);
	}
}
