package dcraft.util.pdf;

import org.apache.pdfbox.pdmodel.PDPageContentStream;


/**
 * Called if an object is about to be drawn.
 * 
 * dCA
 */
public interface LineListener {
	LLResult prep(Object drawnObject, Position upperLeft, float height);
	
	public class LLResult {
		public PDPageContentStream stream = null;
		public Position pos = null;
	}
}
