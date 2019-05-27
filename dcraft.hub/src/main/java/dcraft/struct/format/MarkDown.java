package dcraft.struct.format;

import dcraft.hub.op.OperatingContextException;
import dcraft.struct.Struct;
import dcraft.util.json3.Util;
import dcraft.web.md.MarkdownUtil;
import dcraft.xml.XElement;

public class MarkDown implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) throws OperatingContextException {
		String val = Struct.objectToString(value);
		
		if (val == null)
			return FormatResult.result(val);
		
		XElement root = MarkdownUtil.process(val, true);
		
		return FormatResult.result(root);
	}
}
