package dcraft.struct.format;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.struct.Struct;
import dcraft.util.HashUtil;
import dcraft.util.chars.Utf8Encoder;

import java.io.ByteArrayInputStream;

public class Hash implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) throws OperatingContextException {
		// TODO support system vs tenant level - support binary vs string (in hex) mode
		
		value = HashUtil.hash(format, new ByteArrayInputStream(Utf8Encoder.encode(Struct.objectToString(value))));
		
		return FormatResult.result(value);
	}
}
