package dcraft.struct.format;

import dcraft.hub.ResourceHub;
import dcraft.struct.Struct;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class YesNoFormatter implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		Boolean val = Struct.objectToBoolean(value);
		
		if (val == null)
			val = Boolean.FALSE;
		
		value = val ? "Yes" : "No";
		
		return FormatResult.result(value);
	}
}
