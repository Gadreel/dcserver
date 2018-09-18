package dcraft.struct.format;

import dcraft.struct.Struct;
import dcraft.xml.XNode;

public class XmlEscapeFormatter implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		value = XNode.quote(Struct.objectToString(value));
		
		return FormatResult.result(value);
	}
}
