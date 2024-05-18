package dcraft.struct.format;

import dcraft.hub.ResourceHub;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class LocalTimeFormatter implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		// TODO act on a list as well as on a scalar

		if (StringUtil.isEmpty(format))
			format = "h:mm a";

		try {
			if (value != null)
				value = DateTimeFormatter.ofPattern(format)
						.withZone(ZoneId.of(ResourceHub.getResources().getLocale().getDefaultChronology()))
						.format(Struct.objectToTime(value));
		}
		catch (DateTimeException x) {
			// NA - simply ignore it
		}

		return FormatResult.result(value);
	}
}
