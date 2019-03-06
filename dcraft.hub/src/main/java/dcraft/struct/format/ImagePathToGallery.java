package dcraft.struct.format;

import dcraft.struct.Struct;
import dcraft.util.StringUtil;

public class ImagePathToGallery implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		String val = Struct.objectToString(value);
		
		if (StringUtil.isNotEmpty(val)) {
			if (! val.startsWith("/galleries"))
				val = "/galleries" + val;
			
			int pos = val.indexOf(".v");
			
			if (pos == -1)
				val = val + ".v";
			
			if (val.endsWith(".v"))
				val = val + "/" + (StringUtil.isNotEmpty(format) ? format : "full.jpg");
			
			value = val;
		}
		
		return FormatResult.result(value);
	}
}
