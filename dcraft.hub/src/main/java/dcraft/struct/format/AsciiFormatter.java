package dcraft.struct.format;

import dcraft.struct.Struct;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;

public class AsciiFormatter implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		String val = Struct.objectToString(value);
		val = ASCIIFoldingFilter.foldToASCII(val);
		return FormatResult.result(val);
	}
}
