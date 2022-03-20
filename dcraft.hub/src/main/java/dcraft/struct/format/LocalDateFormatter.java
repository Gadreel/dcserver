package dcraft.struct.format;

import dcraft.hub.ResourceHub;
import dcraft.struct.Struct;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class LocalDateFormatter implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		// TODO act on a list as well as on a scalar

		if (value != null)
			value = DateTimeFormatter.ofPattern(format)
					.withZone(ZoneId.of(ResourceHub.getResources().getLocale().getDefaultChronology()))
					.format(Struct.objectToDate(value));
		
		return FormatResult.result(value);
	}
}
