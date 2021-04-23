package dcraft.struct.format;

import dcraft.struct.CompositeStruct;
import dcraft.struct.Struct;
import dcraft.util.json3.Util;

public class PrettyJsonFormatter implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		CompositeStruct val = Struct.objectToComposite(value);
		
		if (val == null)
			return FormatResult.result(val);
		
		value = val.toPrettyString();
		
		return FormatResult.result(value);
	}
}
