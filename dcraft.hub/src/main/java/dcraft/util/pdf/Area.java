package dcraft.util.pdf;

import java.io.IOException;

public interface Area {
    /**
     * @return the width of the area.
     * @throws IOException by pdfbox
     */
    float getWidth() throws IOException;

    /**
     * @return the height of the area.
     * @throws IOException by pdfbox
     */
    float getHeight() throws IOException;
}
