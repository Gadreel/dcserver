package dcraft.struct.format;

import dcraft.hub.ResourceHub;
import dcraft.struct.Struct;

import java.time.DateTimeException;
import java.time.ZoneId;

public class LocalDateTimeFormatter implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		// TODO act on a list as well as on a scalar

		try {
			if (value != null)
				value = java.time.format.DateTimeFormatter.ofPattern(format)
						//.withZone(ZoneId.of(ResourceHub.getResources().getLocale().getDefaultChronology()))
						.format(Struct.objectToDateTime(value));
		}
		catch (DateTimeException x) {
			// NA - simply ignore it
		}

		return FormatResult.result(value);
	}
}
