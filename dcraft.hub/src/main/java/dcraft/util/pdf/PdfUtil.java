package dcraft.util.pdf;

public class PdfUtil {
	
	public static StringBuilder stripAllRestrictedPDFChars(CharSequence str) {
		if (str == null)
			return null;
		
		StringBuilder sb = new StringBuilder();
		
		for (int i = 0; i < str.length(); i++) {
			int ch = PdfUtil.cleanChar(str.charAt(i));
			
			if (ch != 0)
				sb.append((char) ch);
		}
		
		return sb;
	}
	
	public static int cleanChar(int ch) {
		if (ch == (int)0x2014)
			return '-';
		
		if (ch == (int)0x2019)
			return '\'';
		
		if (ch == (int)0x201C)
			return '"';
		
		if (ch == (int)0x201D)
			return '"';
		
		if ((ch <= (int)0x9) || ((ch < (int)0x20) && (ch > (int)0xD)) || (ch >= (int)0x7F))
			return 0;
		
		return ch;
	}
}
