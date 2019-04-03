package dcraft.util.pdf;

import dcraft.log.Logger;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageTree;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class PdfUtil {
	public static List<TextObject> extractTextObjects(Path path) {
		try {
			List<TextObject> textObjectList = new ArrayList<>();

			PDDocument document = PDDocument.load(path.toFile());

			//System.out.println("pages: " + document.getNumberOfPages());

			PDPageTree allPages = document.getDocumentCatalog().getPages();

			for (PDPage page : allPages) {
				PDFStreamParser parser = new PDFStreamParser(page);
				parser.parse();

				List tokens = parser.getTokens();

				boolean parsingTextObject = false; // boolean to check whether the token

				// being parsed is part of a TextObject
				TextObject textobj = new TextObject();

				for (int i = 0; i < tokens.size(); i++) {
					Object next = tokens.get(i);
					if (next instanceof Operator) {
						Operator op = (Operator) next;
						switch (op.getName()) {
							case "BT":
								// BT: Begin Text.
								parsingTextObject = true;
								textobj = new TextObject();
								break;
							case "ET":
								parsingTextObject = false;
								textObjectList.add(textobj);
								break;
							case "Tj":
							case "TJ":
								textobj.setText();
								break;
							case "TM":
							case "Tm":
								textobj.setMatrix();
								break;
							//default:
							//	System.out.println("unsupported operation " + op);
						}
						textobj.clearAllAttributes();
					}
					else if (parsingTextObject) {
						textobj.addAttribute(next);
					}
					else {
						//System.out.println("ignore "+next.getClass()+" -> "+next);
					}
				}
			}

			document.close();

			return textObjectList;
		}
		catch (IOException x) {
			Logger.error("Unable to read PDF file: " + x);
		}

		return null;
	}
	
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
		
		if ((ch <= (int)0x9) || ((ch < (int)0x20) && (ch > (int)0xD)) || (ch == (int)0x7F) || (ch >= (int)0xFF))
			return 0;
		
		return ch;
	}
}
