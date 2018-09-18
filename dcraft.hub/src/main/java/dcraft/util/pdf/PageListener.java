package dcraft.util.pdf;

import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDPageContentStream;

public interface PageListener {
	void finishPage(PDPageContentStream stream, int pagenumber) throws IOException;
}
