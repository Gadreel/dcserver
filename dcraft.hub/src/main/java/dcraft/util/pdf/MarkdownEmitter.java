package dcraft.util.pdf;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;

import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import dcraft.util.pdf.annotations.AnnotatedStyledText;
import dcraft.util.pdf.annotations.Annotation;
import dcraft.util.pdf.annotations.Annotations;
import dcraft.util.pdf.annotations.Annotations.HyperlinkAnnotation.LinkStyle;
import dcraft.web.md.ProcessContext;
import dcraft.web.md.process.Block;
import dcraft.web.md.process.Line;
import dcraft.web.md.process.LinkRef;
import dcraft.web.md.process.MarkToken;
import dcraft.web.md.process.Utils;

public class MarkdownEmitter {
    protected ProcessContext ctx = null;
    protected HashMap<String, LinkRef> linkRefs = new HashMap<String, LinkRef>();

    protected Deque<Color> colors = new ArrayDeque<Color>();
    protected Deque<PDFont> fonts = new ArrayDeque<PDFont>();
    protected Deque<Float> fontsizes = new ArrayDeque<Float>();
    
    public MarkdownEmitter(ProcessContext ctx, QuickPDF pdf) {
        this.ctx = ctx;
        
        this.pushCurrentColor(Color.BLACK);
        this.pushCurrentFontSize(pdf.getFontSize());
        this.fonts.push(pdf.getFont());
    }
    
    public PDFont getCurrentFont() {
    	return this.fonts.peek();
    }
    
    public PDFont popCurrentFont() {
    	return this.fonts.pop();
    }
    
    public void withBold(QuickPDF pdf) {
    	PDFont f = this.getCurrentFont();
    	
    	if ((f == pdf.italicfont) || (f == pdf.bolditalicfont))
    		this.fonts.push(pdf.bolditalicfont);
    	else
    		this.fonts.push(pdf.boldfont);
    }
    
    public void withIalic(QuickPDF pdf) {
    	PDFont f = this.getCurrentFont();
    	
    	if ((f == pdf.boldfont) || (f == pdf.bolditalicfont))
    		this.fonts.push(pdf.bolditalicfont);
    	else
    		this.fonts.push(pdf.italicfont);
    }
    
    public void pushCurrentFont(PDFont v) {
    	this.fonts.push(v);
    }
    
    public Float getCurrentFontSize() {
    	return this.fontsizes.peek();
    }
    
    public Float popCurrentFontSize() {
    	return this.fontsizes.pop();
    }
    
    public void pushCurrentFontSize(Float v) {
    	this.fontsizes.push(v);
    }
    
    public Color getCurrentColor() {
    	return this.colors.peek();
    }
    
    public Color popCurrentColor() {
    	return this.colors.pop();
    }
    
    public void pushCurrentColor(Color v) {
    	this.colors.push(v);
    }
    
    public void addLinkRef(String key, LinkRef linkRef) {
        this.linkRefs.put(key.toLowerCase(), linkRef);
    }

    public void emit(QuickPDF pdf, Block root) throws IOException {
        root.removeSurroundingEmptyLines();
        
        switch(root.type) {
        case RULER:
        	//parent.add(new UIElement("hr"));
            return;
        case IMAGE_BLOCK:
            //this.emitMarkedLines(parent, root.lines);
        	return;
        case XML:
        case PLUGIN:
        	//target = parent;
        	
            return;
            
        case NONE:{
        	if (root.hasLines()) {
                this.emitMarkedLines(pdf, root.lines);
        	}
            break;
        }
        case HEADLINE: {
        	if (root.hasLines()) {
            	this.withBold(pdf);
                this.emitMarkedLines(pdf, root.lines);
                this.popCurrentFont();
        	}
            
            break;
        }
        case PARAGRAPH: {
        	if (root.hasLines()) {
                this.emitMarkedLines(pdf, root.lines);
        	}
            
            break;
        }
        case CODE:
        case FENCED_CODE: {
        	if (root.hasLines()) {
        		this.emitCodeLines(pdf, root.lines, root.meta, true);
        	}
        	
            break;
        }
        case BLOCKQUOTE: {
        	pdf.setIndent(32);
        	
            Block block = root.blocks;
            
            while (block != null) {
                this.emit(pdf, block);
                block = block.next;
            }
            
        	pdf.setIndent(0);
        	
            break;
        }
        case UNORDERED_LIST: {
            Block block = root.blocks;
            
            while (block != null) {
                this.emit(pdf, block);
                block = block.next;
            }
            
            break;
        }
        case ORDERED_LIST: {
            Block block = root.blocks;
            
            while (block != null) {
                this.emit(pdf, block);
                block = block.next;
            }
            
            break;
        }
        case LIST_ITEM: {
        	Position sp = pdf.getPosition();
        	
        	pdf.line("- ");
        	pdf.setPosition(new Position(pdf.getPosition().getX(), sp.getY() + (pdf.getFontSize() / 4 * 3)));
        	pdf.setIndent(18);

            Block block = root.blocks;
            
            while (block != null) {
                this.emit(pdf, block);
                block = block.next;
            }
        	
        	pdf.setIndent(0);
            
            break;
        }
        }
    }


    protected void emitMarkedLines(QuickPDF pdf, Line lines) throws IOException {
    	Paragraph p = pdf.prepParagraph();
    	
        Line line = lines;
        
        while (line != null) {
            if (! line.isEmpty)
            	this.recursiveEmitLine(pdf, p, line.value.substring(line.leading, line.value.length() - line.trailing), 0, MarkToken.NONE);
            
            if (line.next != null) 
                p.add(new NewLine(this.getCurrentFontSize()));
            
            line = line.next;
        }
        
        pdf.wraplines(p);
    }

    protected void emitCodeLines(QuickPDF pdf, Line lines, String meta, boolean removeIndent) throws IOException {
    	Paragraph p = pdf.prepParagraph();
    	
        Line line = lines;

        while (line != null) {
            if (! line.isEmpty)
                p.addText(removeIndent ? line.value.substring(4) : line.value, pdf.getFontSize(), PDType1Font.COURIER);
            
            p.add(new NewLine(this.getCurrentFontSize()));
            
            line = line.next;
        }
        
        pdf.wraplines(p);
    }
    
    /**
     * Finds the position of the given Token in the given String.
     * 
     * @param in
     *            The String to search on.
     * @param start
     *            The starting character position.
     * @param token
     *            The token to find.
     * @return The position of the token or -1 if none could be found.
     */
    private int findToken(String in, int start, MarkToken token)
    {
        int pos = start;
        while(pos < in.length())
        {
            if(this.getToken(in, pos) == token)
                return pos;
            pos++;
        }
        return -1;
    }

    /*
     * Checks if there is a valid markdown link definition.
     */
    protected int emitLink(QuickPDF pdf, Paragraph p, String in, int start, MarkToken token) {
        boolean isAbbrev = false;
        int pos = start + (token == MarkToken.LINK ? 1 : (token == MarkToken.X_IMAGE) ? 3 : 2);
        
        StringBuilder temp = new StringBuilder();
        temp.setLength(0);
        
        pos = Utils.readMdLinkId(temp, in, pos);
        
        if (pos < start)
            return -1;

        String name = temp.toString(), link = null, comment = null;
        int oldPos = pos++;
        pos = Utils.skipSpaces(in, pos);
        
        if (pos < start) {
            LinkRef lr = this.linkRefs.get(name.toLowerCase());
            
            if (lr == null) 
                return -1;
            	
            isAbbrev = lr.isAbbrev;
            link = lr.link;
            comment = lr.title;
            pos = oldPos;
        }
        else if (in.charAt(pos) == '(') {
            pos++;
            pos = Utils.skipSpaces(in, pos);
            
            if (pos < start)
                return -1;
            
            temp.setLength(0);
            boolean useLt = in.charAt(pos) == '<';
            
            pos = useLt ? Utils.readUntil(temp, in, pos + 1, '>') : Utils.readMdLink(temp, in, pos);
            
            if (pos < start)
                return -1;
            
            if (useLt)
                pos++;
            
            link = temp.toString();

            if (in.charAt(pos) == ' ') {
                pos = Utils.skipSpaces(in, pos);
                
                if (pos > start && in.charAt(pos) == '"') {
                    pos++;
                    temp.setLength(0);
                    pos = Utils.readUntil(temp, in, pos, '"');
                    
                    if (pos < start)
                        return -1;
                    
                    comment = temp.toString();
                    pos++;
                    pos = Utils.skipSpaces(in, pos);
                    
                    if (pos == -1)
                        return -1;
                }
            }

            // grab the classes for X_IMAGE
            if (pos > start && in.charAt(pos) == '"') {
                pos++;
                temp.setLength(0);
                pos = Utils.readUntil(temp, in, pos, '"');
                
                if(pos < start)
                    return -1;
                
                //classes = temp.toString();
                pos++;
                pos = Utils.skipSpaces(in, pos);
                
                if(pos == -1)
                    return -1;
            }
            
            if (in.charAt(pos) != ')')
                return -1;
        }
        else if (in.charAt(pos) == '[') {
            pos++;
            temp.setLength(0);
            pos = Utils.readRawUntil(temp, in, pos, ']');
            
            if (pos < start)
                return -1;
            
            String id = (temp.length() > 0) ? temp.toString() : name;
            LinkRef lr = this.linkRefs.get(id.toLowerCase());
            
            if (lr != null) {
                link = lr.link;
                comment = lr.title;
            }
        }
        else {
            LinkRef lr = this.linkRefs.get(name.toLowerCase());
            
            if (lr == null)
                return -1;
            
            isAbbrev = lr.isAbbrev;
            link = lr.link;
            comment = lr.title;
            pos = oldPos;
        }

        if (link == null)
            return -1;

        if (token == MarkToken.LINK) {
            if(isAbbrev && comment != null) {
                // TODO this.recursiveEmitLine(anchr, name, 0, MarkToken.NONE);  
            }
            else {
            	Annotation href = new Annotations.HyperlinkAnnotation(link, LinkStyle.ul); 
            	
            	List<Annotation> hrefs = new ArrayList<>(); 
            	
            	hrefs.add(href);
            	
            	AnnotatedStyledText ltext = new AnnotatedStyledText(name, this.getCurrentFontSize(), this.getCurrentFont(), this.getCurrentColor(), hrefs);
            	
            	p.add(ltext);
            }
        }
        /* TODO not currently supported
        else if (token == MarkToken.IMAGE) {
        	UIElement img = (UIElement) new UIElement("img")
        		.withAttribute("src", link)
        		.withAttribute("alt", name);
        	
            if (comment != null)
            	img.withAttribute("title", comment);
        	
        	parent.add(img);
        }
        else {		// X_IMAGE a captioned image
        	UIElement div = (UIElement) new UIElement("div")
	    		.withAttribute("class", "pure-img-inline " + (classes == null ? "" : classes));
	    		
	    	div.add(new UIElement("img")
	    		.withAttribute("src", link)
	    		.withAttribute("alt", name));
	    	
	        if (comment != null)
	        	div.add(new UIElement("div").withText(comment));
        	
	    	parent.add(div);
        }
        */

        return pos;
    }

    /**
     * Check if there is a valid HTML tag here. This method also transforms auto
     * links and mailto auto links.
     * 
     * @param in
     *            Input String.
     * @param start
     *            Starting position.
     * @return The new position or -1 if nothing valid has been found.
     */
    protected int emitHtml(ProcessContext ctx, String in, int start) {
        StringBuilder temp = new StringBuilder();
        int pos;

        // Check for auto links
        temp.setLength(0);
        
        pos = Utils.readUntil(temp, in, start + 1, ':', ' ', '>', '\n');
        
        if (pos != -1 && in.charAt(pos) == ':' && in.length() > (pos + 2) && in.charAt(pos + 1) == '/' && in.charAt(pos + 2) == '/') {
            pos = Utils.readUntil(temp, in, pos, '>');
            
            if (pos != -1) 
                return pos;
        }

        // Check for mailto or address auto link
        temp.setLength(0);
        pos = Utils.readUntil(temp, in, start + 1, '@', ' ', '>', '\n');
        
        if (pos != -1 && in.charAt(pos) == '@') {
            pos = Utils.readUntil(temp, in, pos, '>');
            
            if (pos != -1) 
                return pos;
        }

        // Check for inline html
        if (start + 2 < in.length()) 
        	return Utils.scanHTML(this.ctx, in, start);

        return -1;
    }
    
    protected int emitEm(QuickPDF pdf, Paragraph p, String in, int start, MarkToken token) throws IOException {
		int b = this.findToken(in, start + 1, token);

		if (b > 0) {
			this.withIalic(pdf);
			this.recursiveEmitLine(pdf, p, in.substring(start + 1, b), 0, token);
			this.popCurrentFont();

			return b;
		}

		return -1;
    }

    protected int emitSuper(QuickPDF pdf, Paragraph p, String in, int start, MarkToken token) throws IOException {
		int b = this.findToken(in, start + 1, token);

		if (b > 0) {
			// TODO not supported yet
			
			this.recursiveEmitLine(pdf, p, in.substring(start + 1, b - 1), 0, token);

			return b;
		}

		return -1;
    }

    protected int emitStrong(QuickPDF pdf, Paragraph p, String in, int start, MarkToken token) throws IOException {
		int b = this.findToken(in, start + 2, token);

		if (b > 0) {
			this.withBold(pdf);
			this.recursiveEmitLine(pdf, p, in.substring(start + 2, b), 0, token);
			this.popCurrentFont();

			return b + 1;
		}

		return -1;
    }

    protected int emitStrike(QuickPDF pdf, Paragraph p, String in, int start, MarkToken token) throws IOException {
		int b = this.findToken(in, start + 2, token);

		if (b > 0) {
			// TODO not supported yet
			
			this.recursiveEmitLine(pdf, p, in.substring(start + 2, b), 0, token);

			return b + 1;
		}

		return -1;
    }
    
    protected int emitEntity(QuickPDF pdf, Paragraph p, String in, int start, MarkToken token) throws IOException {
    	if (in.length() - start < 3)
    		return -1;
    	
    	int pos = -1;
    	
        if (in.charAt(start + 1) == '#') {
            if (in.charAt(start + 2) == 'x' || in.charAt(start + 2) == 'X') {
                if (in.length() - start < 4)
                    return -1;
                
                for (int i = start + 3; i < in.length(); i++) {
                    char c = in.charAt(i);
                    
                    if (c == ';') {
                    	pos = i;
                    	break;
                    }
                    
                    if ((c < '0' || c > '9') && ((c < 'a' || c > 'f') && (c < 'A' || c > 'F')))
                        return -1;
                }
            }
            else {
                for (int i = start + 2; i < in.length(); i++) {
                    char c = in.charAt(i);
                    
                    if (c == ';') {
                    	pos = i;
                    	break;
                    }
                    
                    if (c < '0' || c > '9')
                        return -1;
                }
            }
        }
        else {
            for (int i = start + 1; i < in.length(); i++) {
                char c = in.charAt(i);
                
                if (c == ';') {
                	pos = i;
                	break;
                }
                
                if ((c < 'a' || c > 'z') && (c < 'A' || c > 'Z'))
                    return -1;
            }
        }
        
        p.addText(in.substring(start, pos + 1), pdf.getFontSize(), pdf.getFont());

        return pos;
    }
    
    protected int emitCode(QuickPDF pdf, Paragraph p, String in, int start, MarkToken token) throws IOException {
    	boolean dub = (token == MarkToken.CODE_DOUBLE);
    	
	    int a = start + (dub ? 2 : 1);
	    int b = this.findToken(in, a, token);
	    
	    if (b < start + 1)
	    	return -1;
	    	
        int pos = b + (dub ? 1 : 0);
        
        p.addText(in.substring(a, b), pdf.getFontSize(), PDType1Font.COURIER);
        
        return pos;
    }
    
    /*
     * Recursively scans through the given line, taking care of any markdown
     * stuff.
     */
    protected void recursiveEmitLine(QuickPDF pdf, Paragraph p, String in, int start, MarkToken token) throws IOException {
        int pos = start;
        int b = 0;
        
        while (pos < in.length()) {
            MarkToken mt = this.getToken(in, pos);
            
            if ((token != MarkToken.NONE)
            		&& (mt == token || token == MarkToken.EM_STAR && mt == MarkToken.STRONG_STAR || token == MarkToken.EM_UNDERSCORE
                            && mt == MarkToken.STRONG_UNDERSCORE))
                return;

            switch(mt) {
            case IMAGE:
            case X_IMAGE:
            case LINK:
                b = this.emitLink(pdf, p, in, pos, mt);
                
                if(b > 0) 
                    pos = b;
                else 
                	p.appendText(in.charAt(pos) + "", this.getCurrentFontSize(), this.getCurrentFont());
                
                break;
            case X_LINK_OPEN:
            	b = 0;
            	
                //b = this.recursiveEmitLine(parent, in, pos + 2, MarkToken.X_LINK_CLOSE);
                //b = this.emitXLink(parent, in, pos, mt);
            	/* TODO 
                temp.setLength(0);
                b = this.recursiveEmitLine(temp, in, pos + 2, MarkToken.X_LINK_CLOSE);
                if(b > 0 && this.config.specialLinkEmitter != null)
                {
                    this.config.specialLinkEmitter.emitSpan(out, temp.toString());
                    pos = b + 1;
                }
                else
                {
                    out.append(in.charAt(pos));
                }
                */

                if (b > 0) 
                    pos = b;
                else 
                	p.appendText(in.charAt(pos) + "", this.getCurrentFontSize(), this.getCurrentFont());

                break;
            case EM_STAR:
            case EM_UNDERSCORE:
                b = this.emitEm(pdf, p, in, pos, mt);
                
                if(b > 0) 
                    pos = b;
                else 
                	p.appendText(in.charAt(pos) + "", this.getCurrentFontSize(), this.getCurrentFont());
                
                break;
            case STRONG_STAR:
            case STRONG_UNDERSCORE:
                b = this.emitStrong(pdf, p, in, pos, mt);
                
                if(b > 0) 
                    pos = b;
                else 
                	p.appendText(in.charAt(pos) + "", this.getCurrentFontSize(), this.getCurrentFont());
                
                break;
            case STRIKE:
                b = this.emitStrike(pdf, p, in, pos, mt);
                
                if(b > 0) 
                    pos = b;
                else 
                	p.appendText(in.charAt(pos) + "", this.getCurrentFontSize(), this.getCurrentFont());

                break;
            case SUPER:
                b = this.emitSuper(pdf, p, in, pos, mt);
                
                if(b > 0) 
                    pos = b;
                else 
                	p.appendText(in.charAt(pos) + "", this.getCurrentFontSize(), this.getCurrentFont());
                
                break;
            case CODE_SINGLE:
            case CODE_DOUBLE:
                b = this.emitCode(pdf, p, in, pos, mt);
                
                if(b > 0) 
                    pos = b;
                else 
                	p.appendText(in.charAt(pos) + "", this.getCurrentFontSize(), this.getCurrentFont());
            	
                break;
            case HTML:
                b = this.emitHtml(this.ctx, in, pos);
                
                if(b > 0) 
                    pos = b;
                else 
                	p.appendText(in.charAt(pos) + "", this.getCurrentFontSize(), this.getCurrentFont());

                break;
            case ENTITY:
                b = this.emitEntity(pdf, p, in, pos, mt);
                
                if (b > 0) 
                    pos = b;
                else 
                	p.appendText(in.charAt(pos) + "", this.getCurrentFontSize(), this.getCurrentFont());
                
                break;
            case X_COPY:
            	p.appendText("©", this.getCurrentFontSize(), this.getCurrentFont());
                pos += 2;
                break;
            case X_REG:
            	p.appendText("®", this.getCurrentFontSize(), this.getCurrentFont());
                pos += 2;
                break;
            case X_TRADE:
            	p.appendText("™", this.getCurrentFontSize(), this.getCurrentFont());
                pos += 3;
                break;
            case X_NDASH:
            	p.appendText("--", this.getCurrentFontSize(), this.getCurrentFont());
                pos++;
                break;
            case X_MDASH:
            	p.appendText("---", this.getCurrentFontSize(), this.getCurrentFont());
                pos += 2;
                break;
            case X_HELLIP:
            	p.appendText("...", this.getCurrentFontSize(), this.getCurrentFont());
                pos += 2;
                break;
            case X_RDQUO:
            case X_LAQUO:
            	p.appendText("«", this.getCurrentFontSize(), this.getCurrentFont());
                pos++;
                break;
            case X_LDQUO:
            case X_RAQUO:
            	p.appendText("»", this.getCurrentFontSize(), this.getCurrentFont());
                pos++;
                break;
            case ESCAPE:
                pos++;
                //$FALL-THROUGH$
            default:
            	char ch = in.charAt(pos);
            	
            	//if (ch != '\n')
            	//	parent.append(ch);
            	
            	p.appendText(ch + "", this.getCurrentFontSize(), this.getCurrentFont());
                
            	break;
            }
            
            pos++;
        }
    }

    /**
     * Turns every whitespace character into a space character.
     * 
     * @param c
     *            Character to check
     * @return 32 is c was a whitespace, c otherwise
     */
    private static char whitespaceToSpace(char c) {
        return Character.isWhitespace(c) ? ' ' : c;
    }

    /**
     * Check if there is any markdown Token.
     * 
     * @param in
     *            Input String.
     * @param pos
     *            Starting position.
     * @return The Token.
     */
    protected MarkToken getToken(String in, int pos) {
        char c0 = pos > 0 ? whitespaceToSpace(in.charAt(pos - 1)) : ' ';
        char c = whitespaceToSpace(in.charAt(pos));
        char c1 = pos + 1 < in.length() ? whitespaceToSpace(in.charAt(pos + 1)) : ' ';
        char c2 = pos + 2 < in.length() ? whitespaceToSpace(in.charAt(pos + 2)) : ' ';
        char c3 = pos + 3 < in.length() ? whitespaceToSpace(in.charAt(pos + 3)) : ' ';

        switch(c)
        {
        case '*':
            if(c1 == '*')
            {
                return c0 != ' ' || c2 != ' ' ? MarkToken.STRONG_STAR : MarkToken.EM_STAR;
            }
            return c0 != ' ' || c1 != ' ' ? MarkToken.EM_STAR : MarkToken.NONE;
        case '_':
            if(c1 == '_')
            {
                return c0 != ' ' || c2 != ' ' ? MarkToken.STRONG_UNDERSCORE : MarkToken.EM_UNDERSCORE;
            }

            return Character.isLetterOrDigit(c0) && c0 != '_' && Character.isLetterOrDigit(c1) ? MarkToken.NONE : MarkToken.EM_UNDERSCORE;
        case '~':
            if(c1 == '~')
            {
                return MarkToken.STRIKE;
            }
            return MarkToken.NONE;
        case '!':
            if((c1 == '!') && (c2 == '['))
                return MarkToken.X_IMAGE;
            if(c1 == '[')
                return MarkToken.IMAGE;
            return MarkToken.NONE;
        case '[':
            if(c1 == '[')
                return MarkToken.X_LINK_OPEN;
            return MarkToken.LINK;
        case ']':
            if(c1 == ']')
                return MarkToken.X_LINK_CLOSE;
            return MarkToken.NONE;
        case '`':
            return c1 == '`' ? MarkToken.CODE_DOUBLE : MarkToken.CODE_SINGLE;
        case '\\':
            switch(c1)
            {
            case '\\':
            case '[':
            case ']':
            case '(':
            case ')':
            case '{':
            case '}':
            case '#':
            case '"':
            case '\'':
            case '.':
            case '>':
            case '<':
            case '*':
            case '+':
            case '-':
            case '_':
            case '!':
            case '`':
            case '^':
                return MarkToken.ESCAPE;
            default:
                return MarkToken.NONE;
            }
        case '<':
            if(c1 == '<')
                return MarkToken.X_LAQUO;
            return MarkToken.HTML;
        case '&':
            return MarkToken.ENTITY;
        case '-':
            if(c1 == '-')
                return c2 == '-' ? MarkToken.X_MDASH : MarkToken.X_NDASH;
            break;
        case '^':
            return c0 == '^' || c1 == '^' ? MarkToken.NONE : MarkToken.SUPER;
        case '>':
            if(c1 == '>')
                return MarkToken.X_RAQUO;
            break;
        case '.':
            if(c1 == '.' && c2 == '.')
                return MarkToken.X_HELLIP;
            break;
        case '(':
            if(c1 == 'C' && c2 == ')')
                return MarkToken.X_COPY;
            if(c1 == 'R' && c2 == ')')
                return MarkToken.X_REG;
            if(c1 == 'T' & c2 == 'M' & c3 == ')')
                return MarkToken.X_TRADE;
            break;
        case '"':
            if(!Character.isLetterOrDigit(c0) && c1 != ' ')
                return MarkToken.X_LDQUO;
            if(c0 != ' ' && !Character.isLetterOrDigit(c1))
                return MarkToken.X_RDQUO;
            break;
        default:
            return MarkToken.NONE;
        }
        
        return MarkToken.NONE;
    }
        
}
