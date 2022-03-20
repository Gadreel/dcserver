package dcraft.struct.format;

import dcraft.struct.Struct;

import java.math.BigDecimal;
import java.text.DecimalFormat;

public class DecimalFormatter implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		// TODO act on a list as well as on a scalar

		BigDecimal val = Struct.objectToDecimal(value);
		
		if (val == null)
			val = BigDecimal.ZERO;
		
		DecimalFormat fmt = new DecimalFormat(format.equals("Money") ? "#,##0.00" : format);
		
		value = fmt.format(val);
		
		return FormatResult.result(value);
	}
}
