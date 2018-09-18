package dcraft.struct.format;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.struct.Struct;

public class Encrypt implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) throws OperatingContextException {
		// TODO support system vs tenant level - support binary vs string (in hex) mode
		
		value = OperationContext.getOrThrow().getTenant().getObfuscator().encryptStringToHex(Struct.objectToString(value));
		
		return FormatResult.result(value);
	}
}
