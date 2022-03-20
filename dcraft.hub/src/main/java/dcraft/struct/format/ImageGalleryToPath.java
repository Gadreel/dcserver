package dcraft.struct.format;

import dcraft.struct.Struct;
import dcraft.util.StringUtil;

public class ImageGalleryToPath implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		// TODO act on a list as well as on a scalar

		String val = Struct.objectToString(value);
		
		if (StringUtil.isNotEmpty(val)) {
			if (val.startsWith("/galleries"))
				val = val.substring(10);
			
			int pos = val.indexOf(".v");
			
			if (pos != -1)
				val = val.substring(0, pos);
			
			value = val;
		}
		
		return FormatResult.result(value);
	}
}
