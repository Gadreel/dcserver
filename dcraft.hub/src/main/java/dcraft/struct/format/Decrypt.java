package dcraft.struct.format;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.struct.Struct;
import dcraft.xml.XNode;

public class Decrypt implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) throws OperatingContextException {
		// TODO support system vs tenant level - support binary vs string (in hex) mode
		
		value = OperationContext.getOrThrow().getTenant().getObfuscator().decryptHexToString(Struct.objectToString(value));
		
		return FormatResult.result(value);
	}
}
