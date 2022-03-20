package dcraft.struct.format;

import dcraft.struct.Struct;
import dcraft.util.StringUtil;

public class ImagePathToGallery implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		// TODO act on a list as well as on a scalar

		String val = Struct.objectToString(value);
		
		String vari = format;
		
		if (StringUtil.isNotEmpty(vari)) {
			if (vari.indexOf('.') == -1)
				vari = vari + ".jpg";			// TODO support lookup from meta
		}
		
		if (StringUtil.isNotEmpty(val)) {
			if (! val.startsWith("/galleries"))
				val = "/galleries" + val;
			
			int pos = val.indexOf(".v");
			
			if (pos == -1) {
				val = val + ".v";
				val.indexOf(".v");
			}
			
			if (val.endsWith(".v")) {
				val = val + "/" + (StringUtil.isNotEmpty(format) ? vari : "full.jpg");
			}
			else if (StringUtil.isNotEmpty(vari)) {
				val = val.substring(0, pos + 2) + "/" + vari;
			}
			
			value = val;
		}
		
		return FormatResult.result(value);
	}
}
