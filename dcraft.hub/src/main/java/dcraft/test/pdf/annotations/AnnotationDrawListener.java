package dcraft.test.pdf.annotations;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination;

import dcraft.test.pdf.Alignment;
import dcraft.test.pdf.CompatibilityHelper;
import dcraft.test.pdf.DrawContext;
import dcraft.test.pdf.DrawListener;
import dcraft.test.pdf.DrawableText;
import dcraft.test.pdf.Position;
import dcraft.test.pdf.annotations.Annotations.AnchorAnnotation;
import dcraft.test.pdf.annotations.Annotations.HyperlinkAnnotation;
import dcraft.test.pdf.annotations.Annotations.HyperlinkAnnotation.LinkStyle;

/**
 * This listener has to be passed to all
 * {@link DrawableText#drawText(org.apache.pdfbox.pdmodel.edit.PDPageContentStream, Position, Alignment, DrawListener)
 * draw()} methods, in order collect all annotation metadata. After all drawing
 * is done, you have to call {@link #finalizeAnnotations()} which creates all
 * necessary annotations and sets them to the corresponding pages. This listener
 * is used by the the rendering API, but you may also use it with the low-level
 * text API.
 */
public class AnnotationDrawListener implements DrawListener {

    private final DrawContext drawContext;
    private Map<String, PageAnchor> anchorMap = new HashMap<String, PageAnchor>();
    private Map<PDPage, List<Hyperlink>> linkMap = new HashMap<PDPage, List<Hyperlink>>();

    /**
     * Creates an AnnotationDrawListener with the given {@link DrawContext}.
     * 
     * @param drawContext
     *            the context which provides the {@link PDDocument} and the
     *            {@link PDPage} currently drawn to.
     */
    public AnnotationDrawListener(final DrawContext drawContext) {
	this.drawContext = drawContext;
    }

    @Override
    public void drawn(Object drawnObject, Position upperLeft, float width,
	    float height) {
	if (!(drawnObject instanceof AnnotatedStyledText)) {
	    return;
	}

	AnnotatedStyledText annotatedText = (AnnotatedStyledText) drawnObject;
	handleHyperlinkAnnotations(annotatedText, upperLeft, width, height);
	handleAnchorAnnotations(annotatedText, upperLeft);
    }

    protected void handleAnchorAnnotations(AnnotatedStyledText annotatedText,
	    Position upperLeft) {
	Iterable<AnchorAnnotation> anchorAnnotations = annotatedText
		.getAnnotationsOfType(AnchorAnnotation.class);
	for (AnchorAnnotation anchorAnnotation : anchorAnnotations) {
	    anchorMap.put(
		    anchorAnnotation.getAnchor(),
		    new PageAnchor(drawContext.getCurrentPage(), upperLeft
			    .getX(), upperLeft.getY()));
	}
    }

    protected void handleHyperlinkAnnotations(
	    AnnotatedStyledText annotatedText, Position upperLeft, float width,
	    float height) {
	Iterable<HyperlinkAnnotation> hyperlinkAnnotations = annotatedText
		.getAnnotationsOfType(HyperlinkAnnotation.class);
	for (HyperlinkAnnotation hyperlinkAnnotation : hyperlinkAnnotations) {
	    List<Hyperlink> links = linkMap.get(drawContext.getCurrentPage());
	    if (links == null) {
		links = new ArrayList<Hyperlink>();
		linkMap.put(drawContext.getCurrentPage(), links);
	    }
	    PDRectangle bounds = new PDRectangle();
	    bounds.setLowerLeftX(upperLeft.getX());
	    bounds.setLowerLeftY(upperLeft.getY() - height);
	    bounds.setUpperRightX(upperLeft.getX() + width);
	    bounds.setUpperRightY(upperLeft.getY());

	    links.add(new Hyperlink(bounds, annotatedText.getColor(),
		    hyperlinkAnnotation.getLinkStyle(), hyperlinkAnnotation
			    .getHyperlinkURI()));
	}
    }

    /**
     * Creates all necessary annotations and sets them to the corresponding
     * pages.
     * 
     * @throws IOException by pdfbox
     */
    public void finalizeAnnotations() throws IOException {
	for (Entry<PDPage, List<Hyperlink>> entry : linkMap.entrySet()) {
	    PDPage page = entry.getKey();
	    List<Hyperlink> links = entry.getValue();
	    for (Hyperlink hyperlink : links) {
		PDAnnotationLink pdLink = null;
		if (hyperlink.getHyperlinkURI().startsWith("#")) {
		    pdLink = createGotoLink(hyperlink);
		} else {
		    pdLink = dcraft.test.pdf.CompatibilityHelper.createLink(
			    hyperlink.getRect(), hyperlink.getColor(),
			    hyperlink.getLinkStyle(), hyperlink.getHyperlinkURI());
		}
		page.getAnnotations().add(pdLink);
	    }

	}
    }

    private PDAnnotationLink createGotoLink(Hyperlink hyperlink) {
	String anchor = hyperlink.getHyperlinkURI().substring(1);
	PageAnchor pageAnchor = anchorMap.get(anchor);
	if (pageAnchor == null) {
	    throw new IllegalArgumentException(String.format(
		    "anchor named '%s' not found", anchor));
	}
	PDPageXYZDestination xyzDestination = new PDPageXYZDestination();
	xyzDestination.setPage(pageAnchor.getPage());
	xyzDestination.setLeft((int) pageAnchor.getX());
	xyzDestination.setTop((int) pageAnchor.getY());
	return CompatibilityHelper.createLink(hyperlink.getRect(),
		hyperlink.getColor(), hyperlink.getLinkStyle(), xyzDestination);
    }

    private static class PageAnchor {
	private final PDPage page;
	private final float x;
	private final float y;

	public PageAnchor(PDPage page, float x, float y) {
	    this.page = page;
	    this.x = x;
	    this.y = y;
	}

	public PDPage getPage() {
	    return page;
	}

	public float getX() {
	    return x;
	}

	public float getY() {
	    return y;
	}

	@Override
	public String toString() {
	    return "PageAnchor [page=" + page + ", x=" + x + ", y=" + y + "]";
	}

    }

    private static class Hyperlink {
	private final PDRectangle rect;
	private final Color color;
	private final String hyperlinkUri;
	private final LinkStyle linkStyle;

	public Hyperlink(PDRectangle rect, Color color, LinkStyle linkStyle,
		String hyperlinkUri) {
	    this.rect = rect;
	    this.color = color;
	    this.hyperlinkUri = hyperlinkUri;
	    this.linkStyle = linkStyle;
	}

	public PDRectangle getRect() {
	    return rect;
	}

	public Color getColor() {
	    return color;
	}

	public String getHyperlinkURI() {
	    return hyperlinkUri;
	}

	public LinkStyle getLinkStyle() {
	    return linkStyle;
	}

	@Override
	public String toString() {
	    return "Hyperlink [rect=" + rect + ", color=" + color
		    + ", hyperlinkUri=" + hyperlinkUri + ", linkStyle=" + linkStyle
		    + "]";
	}

    }
}
