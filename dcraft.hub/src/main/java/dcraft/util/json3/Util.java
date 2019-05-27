package dcraft.util.json3;

import dcraft.util.StringUtil;

public class Util {
	// TODO - improve this, why are there no simple examples of this
	static public String decodeJsonString(String v) {
		if (StringUtil.isEmpty(v))
			return v;
		
		return v
				.replace("\\n", "\n")
				.replace("\\t", "\t")
				.replace("\\\\", "\\")
				.replace("\\\"", "\"")
				;
	}
	
	static public String encodeJsonString(String v) {
		if (StringUtil.isEmpty(v))
			return v;
		
		return v
				.replace("\n", "\\n")
				.replace("\t", "\\t")
				.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				;
	}
}
