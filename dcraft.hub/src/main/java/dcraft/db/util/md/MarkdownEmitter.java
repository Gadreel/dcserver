package dcraft.db.util.md;

import dcraft.db.util.DocumentIndexBuilder;
import dcraft.locale.LocaleUtil;
import dcraft.web.md.ProcessContext;
import dcraft.web.md.process.*;
import dcraft.web.ui.inst.W3;

import java.io.IOException;
import java.util.*;

public class MarkdownEmitter {
    protected ProcessContext ctx = null;
    protected HashMap<String, LinkRef> linkRefs = new HashMap<>();

    public MarkdownEmitter(ProcessContext ctx, DocumentIndexBuilder indexBuilder) {
        this.ctx = ctx;
    }

    public void addLinkRef(String key, LinkRef linkRef) {
        this.linkRefs.put(key.toLowerCase(), linkRef);
    }

    public void emit(DocumentIndexBuilder indexBuilder, Block root) throws IOException {
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
                this.emitMarkedLines(indexBuilder, root.lines);
        	}
            break;
        }
        case HEADLINE: {
        	if (root.hasLines()) {
                int score = 7 - root.hlDepth;
            	indexBuilder.adjustScore(score);
                this.emitMarkedLines(indexBuilder, root.lines);
				indexBuilder.adjustScore(-score);
        	}
            
            break;
        }
        case PARAGRAPH: {
        	if (root.hasLines()) {
                this.emitMarkedLines(indexBuilder, root.lines);
        	}
            
            break;
        }
        case CODE:
        case FENCED_CODE: {
        	if (root.hasLines()) {
        		this.emitCodeLines(indexBuilder, root.lines, root.meta, true);
        	}
        	
            break;
        }
        case BLOCKQUOTE: {
            Block block = root.blocks;
            
            while (block != null) {
                this.emit(indexBuilder, block);
                block = block.next;
            }

            break;
        }
        case UNORDERED_LIST: {
            Block block = root.blocks;
            
            while (block != null) {
                this.emit(indexBuilder, block);
                block = block.next;
            }
            
            break;
        }
        case ORDERED_LIST: {
            Block block = root.blocks;
            
            while (block != null) {
                this.emit(indexBuilder, block);
                block = block.next;
            }
            
            break;
        }
        case LIST_ITEM: {
            Block block = root.blocks;
            
            while (block != null) {
                this.emit(indexBuilder, block);
                block = block.next;
            }

            break;
        }
        }
    }


    protected void emitMarkedLines(DocumentIndexBuilder indexBuilder, Line lines) throws IOException {
    	indexBuilder.startSection();

        Line line = lines;
        
        while (line != null) {
            if (! line.isEmpty)
            	this.recursiveEmitLine(indexBuilder, line.value.substring(line.leading, line.value.length() - line.trailing), 0, MarkToken.NONE);

            line = line.next;
        }

        indexBuilder.endSection();
    }

    protected void emitCodeLines(DocumentIndexBuilder indexBuilder, Line lines, String meta, boolean removeIndent) throws IOException {
        Line line = lines;

        while (line != null) {
        	/* currently don't index Code
            if (! line.isEmpty)
                p.addText(removeIndent ? line.value.substring(4) : line.value, pdf.getFontSize(), PDType1Font.COURIER);
            
            p.add(new NewLine(this.getCurrentFontSize()));
            */
            
            line = line.next;
        }
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
    protected int emitLink(DocumentIndexBuilder indexBuilder, String in, int start, MarkToken token) {
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
            	// TODO support content/document position
				indexBuilder.withFragments(
						LocaleUtil.full(name, this.ctx.getLang()),
						1
				);

				indexBuilder.withSpecialText(name);
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
    protected int emitHtml(String in, int start) {
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
    
    protected int emitEm(DocumentIndexBuilder indexBuilder, String in, int start, MarkToken token) throws IOException {
		int b = this.findToken(in, start + 1, token);

		if (b > 0) {
			indexBuilder.adjustScore(1);
			this.recursiveEmitLine(indexBuilder, in.substring(start + 1, b), 0, token);
			indexBuilder.adjustScore(-1);

			return b;
		}

		return -1;
    }

    protected int emitSuper(DocumentIndexBuilder indexBuilder, String in, int start, MarkToken token) throws IOException {
		int b = this.findToken(in, start + 1, token);

		if (b > 0) {
			// TODO not supported yet
			
			this.recursiveEmitLine(indexBuilder, in.substring(start + 1, b - 1), 0, token);

			return b;
		}

		return -1;
    }

    protected int emitStrong(DocumentIndexBuilder indexBuilder, String in, int start, MarkToken token) throws IOException {
		int b = this.findToken(in, start + 2, token);

		if (b > 0) {
			indexBuilder.adjustScore(1);
			this.recursiveEmitLine(indexBuilder, in.substring(start + 2, b), 0, token);
			indexBuilder.adjustScore(-1);

			return b + 1;
		}

		return -1;
    }

    protected int emitStrike(DocumentIndexBuilder indexBuilder, String in, int start, MarkToken token) throws IOException {
		int b = this.findToken(in, start + 2, token);

		if (b > 0) {
			// TODO not supported yet
			
			this.recursiveEmitLine(indexBuilder, in.substring(start + 2, b), 0, token);

			return b + 1;
		}

		return -1;
    }
    
    protected int emitEntity(DocumentIndexBuilder indexBuilder, String in, int start, MarkToken token) throws IOException {
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

        /* doesn't index entities currently
		// TODO support content/document position
		indexBuilder.withFragments(
				LocaleUtil.full(in.substring(start, pos + 1), this.ctx.getLang()),
				this.scorelevel
		);
		*/

        return pos;
    }
    
    protected int emitCode(DocumentIndexBuilder indexBuilder, String in, int start, MarkToken token) throws IOException {
    	boolean dub = (token == MarkToken.CODE_DOUBLE);
    	
	    int a = start + (dub ? 2 : 1);
	    int b = this.findToken(in, a, token);
	    
	    if (b < start + 1)
	    	return -1;
	    	
        int pos = b + (dub ? 1 : 0);

        /* doesn't index code currently
		// TODO support content/document position
		indexBuilder.withFragments(
				LocaleUtil.full(in.substring(a, b), this.ctx.getLang()),
				this.scorelevel
		);
        */

        return pos;
    }
    
    /*
     * Recursively scans through the given line, taking care of any markdown
     * stuff.
     */
    protected void recursiveEmitLine(DocumentIndexBuilder indexBuilder, String in, int start, MarkToken token) throws IOException {
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
                b = this.emitLink(indexBuilder, in, pos, mt);
                
                if(b > 0) 
                    pos = b;
                else 
                	indexBuilder.withChar(in.charAt(pos));
                
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
					indexBuilder.withChar(in.charAt(pos));

                break;
            case EM_STAR:
            case EM_UNDERSCORE:
                b = this.emitEm(indexBuilder, in, pos, mt);
                
                if(b > 0) 
                    pos = b;
                else
					indexBuilder.withChar(in.charAt(pos));

                break;
            case STRONG_STAR:
            case STRONG_UNDERSCORE:
                b = this.emitStrong(indexBuilder, in, pos, mt);
                
                if(b > 0) 
                    pos = b;
                else
					indexBuilder.withChar(in.charAt(pos));

                break;
            case STRIKE:
                b = this.emitStrike(indexBuilder, in, pos, mt);
                
                if(b > 0) 
                    pos = b;
                else
					indexBuilder.withChar(in.charAt(pos));

                break;
            case SUPER:
                b = this.emitSuper(indexBuilder, in, pos, mt);
                
                if(b > 0) 
                    pos = b;
                else
					indexBuilder.withChar(in.charAt(pos));

                break;
            case CODE_SINGLE:
            case CODE_DOUBLE:
                b = this.emitCode(indexBuilder, in, pos, mt);
                
                if(b > 0) 
                    pos = b;
                else
					indexBuilder.withChar(in.charAt(pos));

                break;
            case HTML:
                b = this.emitHtml(in, pos);
                
                if(b > 0) 
                    pos = b;
                else
					indexBuilder.withChar(in.charAt(pos));

                break;
            case ENTITY:
                b = this.emitEntity(indexBuilder, in, pos, mt);
                
                if (b > 0) 
                    pos = b;
                else
					indexBuilder.withChar(in.charAt(pos));
                
                break;
            case X_COPY:
            	// not supported in index
                pos += 2;
                break;
            case X_REG:
                pos += 2;
                break;
            case X_TRADE:
                pos += 3;
                break;
            case X_NDASH:
                pos++;
                break;
            case X_MDASH:
                pos += 2;
                break;
            case X_HELLIP:
                pos += 2;
                break;
            case X_RDQUO:
            case X_LAQUO:
                pos++;
                break;
            case X_LDQUO:
            case X_RAQUO:
                pos++;
                break;
            case ESCAPE:
                pos++;
                //$FALL-THROUGH$
            default:
            	char ch = in.charAt(pos);
            	
            	//if (ch != '\n')
            	//	parent.append(ch);

				indexBuilder.withChar(ch);

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
