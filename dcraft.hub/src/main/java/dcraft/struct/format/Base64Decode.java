package dcraft.struct.format;

import dcraft.struct.ListStruct;
import dcraft.struct.Struct;
import dcraft.util.Base64;
import dcraft.util.StringUtil;

import java.nio.charset.StandardCharsets;

public class Base64Decode extends ListAwareFormatter {
	public Base64Decode() {
		this.ignoreIfFail = true;
	}

	@Override
	public Object formatInternal(Object value, String op, String format) {
		return Base64Decode.decodeToUtf8(Struct.objectToString(value));
	}

	static public String decodeToUtf8(String v) {
		if (StringUtil.isNotEmpty(v))
			// for now String mode turn B64 string into normal string
			return Base64.decodeToUtf8(v);

		return null;
	}
}
