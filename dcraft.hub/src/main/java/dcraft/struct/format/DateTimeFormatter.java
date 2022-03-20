package dcraft.struct.format;

import dcraft.hub.ResourceHub;
import dcraft.struct.Struct;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.ZoneId;

public class DateTimeFormatter implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		// TODO act on a list as well as on a scalar

		if (value != null)
			value = java.time.format.DateTimeFormatter.ofPattern(format)
					.withZone(ZoneId.of(ResourceHub.getResources().getLocale().getDefaultChronology()))
					.format(Struct.objectToDateTime(value));

		return FormatResult.result(value);
	}
}
