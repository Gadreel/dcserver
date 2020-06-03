package dcraft.struct.format;

import dcraft.struct.Struct;
import dcraft.util.pdf.PdfUtil;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;

public class PdfTextFormatter implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		String val = Struct.objectToString(value);
		StringBuilder val2 = PdfUtil.stripAllRestrictedPDFChars(val);
		return FormatResult.result(val2.toString());
	}
}
