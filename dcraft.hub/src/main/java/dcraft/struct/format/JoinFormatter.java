package dcraft.struct.format;

import dcraft.struct.ListStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;

public class JoinFormatter implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		if (value instanceof ListStruct) {
			String delim = (format != null) ? format : ", ";
			
			value = String.join(delim, ((ListStruct) value).toStringList());
		}
		
		return FormatResult.result(value);
	}
}
