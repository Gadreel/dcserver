package dcraft.web.md.process;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.script.ScriptHub;
import dcraft.script.inst.doc.Base;
import dcraft.util.StringUtil;
import dcraft.web.md.Plugin;
import dcraft.web.md.ProcessContext;
import dcraft.web.ui.inst.W3;
import dcraft.web.ui.inst.W3Closed;
import dcraft.xml.XElement;
import dcraft.xml.XText;

public class Emitter {
    protected ProcessContext ctx = null;
    protected HashMap<String, LinkRef> linkRefs = new HashMap<String, LinkRef>();
    protected Map<String, Plugin> plugins = new HashMap<String, Plugin>();
    
    public Emitter(ProcessContext ctx) {
        this.ctx = ctx;
        
        for(Plugin plugin : ctx.getConfig().getPlugins()) 
          	register(plugin);
    }
    
	public void register(Plugin plugin) {
		plugins.put(plugin.getIdPlugin(), plugin);
	}
    
    public void addLinkRef(String key, LinkRef linkRef) {
        this.linkRefs.put(key.toLowerCase(), linkRef);
    }

    public void emit(XElement parent, Block root) throws OperatingContextException {
        root.removeSurroundingEmptyLines();

        XElement target = null;
        
        switch(root.type) {
        case RULER:
        	parent.add(W3Closed.tag("hr"));
            return;
        case IMAGE_BLOCK:
            this.emitMarkedLines(parent, root.lines);
        	return;
        case NONE:
        case XML:
        case PLUGIN:
        	target = parent;
        	
            break;
        case HEADLINE: {
        	W3 hdr = W3.tag("h" + root.hlDepth);
        	
            if (root.id != null)
            	hdr.setAttribute("id", root.id);
            
			target = W3.tag("span");
			
			hdr.with(target);
			
        	parent.add(hdr);
            
            break;
        }
        case PARAGRAPH:
        	target = W3.tag("p");
        	
            if (root.id != null)
            	target.setAttribute("id", root.id);
            
        	parent.add(target);
            
            break;
        case CODE:
        case FENCED_CODE:
        	//XElement targetparent = new XElement("pre");
        	target = W3.tag("code");
        	
            if (root.id != null)
            	target.setAttribute("id", root.id);
            
            //targetparent.add(target);
        	//parent.add(targetparent);
            
        	parent.add(target);
            
            break;
        case BLOCKQUOTE:
        	target = W3.tag("blockquote");
        	
            if (root.id != null)
            	target.setAttribute("id", root.id);
            
        	parent.add(target);
            
            break;
        case UNORDERED_LIST:
        	target = W3.tag("ul");
        	
            if (root.id != null)
            	target.setAttribute("id", root.id);
            
        	parent.add(target);
            
            break;
        case ORDERED_LIST:
        	target = W3.tag("ol");
        	
            if (root.id != null)
            	target.setAttribute("id", root.id);
            
        	parent.add(target);
            
            break;
        case LIST_ITEM:
        	target = W3.tag("li");
        	
            if (root.id != null)
            	target.setAttribute("id", root.id);
            
        	parent.add(target);
            
            break;
        }
        
        if (root.type == BlockType.PLUGIN) {
            this.emitPluginLines(target, root.lines, root.meta);
        	return;
        }

        if(root.hasLines())  {
            switch(root.type)
            {
            case CODE:
                this.emitCodeLines(target, root.lines, root.meta, true);
                break;
            case FENCED_CODE:
                this.emitCodeLines(target, root.lines, root.meta, false);
                break;
            case XML:
                this.emitRawLines(target, root.lines);
                break;
            default:
                this.emitMarkedLines(target, root.lines);
                break;
            }
        }
        else {
            Block block = root.blocks;
            
            while (block != null) {
                this.emit(target, block);
                block = block.next;
            }
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
    protected int emitLink(XElement parent, String in, int start, MarkToken token) throws OperatingContextException {
        boolean isAbbrev = false;
        int pos = start + (token == MarkToken.LINK ? 1 : (token == MarkToken.X_IMAGE) ? 3 : 2);
        
        StringBuilder temp = new StringBuilder();
        temp.setLength(0);
        
        pos = Utils.readMdLinkId(temp, in, pos);
        
        if (pos < start)
            return -1;

        String name = temp.toString(), link = null, comment = null, classes = null;
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
                
                classes = temp.toString();
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
            	XElement anchr = W3.tag("abbr")
	        		.withAttribute("title", comment);
            	
                this.recursiveEmitLine(anchr, name, 0, MarkToken.NONE);  
            }
            else {
            	XElement anchr = W3.tag("a")
	        		.withAttribute("href", link)
	        		.withAttribute("alt", name)
					.withAttribute("class", (classes == null ? "" : classes))
	        		.withAttribute("data-dc-tag", "dc.Link")
	        		.withAttribute("data-dc-enhance", "true");

				if(StringUtil.isNotEmpty(comment))
					anchr.withAttribute("aria-label", comment);

            	parent.add(anchr);

                this.recursiveEmitLine(anchr, name, 0, MarkToken.NONE);  
            }
        }
        else if (token == MarkToken.IMAGE) {
        	XElement img = W3Closed.tag("img")
				.withAttribute("class", "pure-img-inline " + (classes == null ? "" : classes))
        		.withAttribute("src", link)
        		.withAttribute("alt", name);
        	
            if (comment != null)
            	img.withAttribute("title", comment);
        	
        	parent.with(img);
        }
        else {		// X_IMAGE a captioned image
        	XElement div = W3.tag("div")
	    		.withAttribute("class", (classes == null ? "" : classes))
				.with(
	    			W3.tag("img")
						.withAttribute("src", link)
						.withAttribute("alt", name)
						.withAttribute("class", "pure-img-inline")
				);
	    	
	        if (comment != null)
	        	div.add(W3.tag("div").withText(comment));
        	
	    	parent.with(div);
        }

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
    protected int emitHtml(XElement parent, String in, int start) throws OperatingContextException {
        StringBuilder temp = new StringBuilder();
        int pos;

        // Check for auto links
        temp.setLength(0);
        
        pos = Utils.readUntil(temp, in, start + 1, ':', ' ', '>', '\n');
        
        if (pos != -1 && in.charAt(pos) == ':' && in.length() > (pos + 2) && in.charAt(pos + 1) == '/' && in.charAt(pos + 2) == '/') {
            pos = Utils.readUntil(temp, in, pos, '>');
            
            if (pos != -1) {
                String link = temp.toString();
                
                parent.with(
                		W3.tag("a")
                			.withAttribute("href", link)
			        		.withAttribute("data-dc-tag", "dc.Link")
			        		.withAttribute("data-dc-enhance", "true")
                			.withText(link)
                );
                
                return pos;
            }
        }

        // Check for mailto or address auto link
        temp.setLength(0);
        pos = Utils.readUntil(temp, in, start + 1, '@', ' ', '>', '\n');
        
        if (pos != -1 && in.charAt(pos) == '@') {
            pos = Utils.readUntil(temp, in, pos, '>');
            
            if (pos != -1) {
                String link = temp.toString();
                
                XElement xml = W3.tag("a");
                
                xml
	        		.withAttribute("data-dc-tag", "dc.Link")
	        		.withAttribute("data-dc-enhance", "true");
               
                parent.with(xml);
                
                //address auto links
                if(link.startsWith("@")) {
                	String slink = link.substring(1);
            		String url = "https://maps.google.com/maps?q=" + slink.replace(' ', '+');
                    
                    xml.withAttribute("href", url).withText(slink);
                }
                //mailto auto links
                else {
                    xml.withAttribute("href", "mailto:" + link).withText(link);
                }
                
                return pos;
            }
        }

        // Check for inline html
        if (start + 2 < in.length()) {
            //temp.setLength(0);
            //pos = Utils.readXML(temp, in, start, this.config.safeMode);
            
        	pos = Utils.scanHTML(in, start);
        	
            if (pos > 0) {
            	String xml = in.substring(start, pos + 1);
	
				if (! this.ctx.getConfig().getSafeMode()) {
					String[] lines = xml.split("\n");
					
					for (int i = 0; i < lines.length; i++) {
						parent.with(XText.of(lines[i]));
						
						if (i < lines.length - 1)
							parent.with(W3Closed.tag("br"));
					}
				}
				else {
					XElement xres = ScriptHub.parseInstructions(xml);
					
					if (xres != null)
						parent.with(xres);
					else
						parent.with(XText.of(xml));
				}
            }
            
            return pos;
        }

        return -1;
    }

    /**
     * Check if this is a valid XML/HTML entity.
     * 
     * @param in
     *            Input String.
     * @param start
     *            Starting position
     * @return The new position or -1 if this entity in invalid.
     */
    protected int emitEntity(XElement parent, String in, int start, MarkToken token) {
    	/*
        int pos = start;
        
        while (pos < in.length()) {
            if (in.charAt(pos) == ';')
                break;
            
            pos++;
        }
    	
        // nothing found
    	if ((pos == in.length()) || (pos - start < 3))
    		return -1;
        */
    	
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
                
                if ((c < 'a' || c > 'z') && (c < 'A' || c > 'Z') && (c < '0' || c > '9'))
                    return -1;
            }
        }
        
        parent.appendEntity(in.substring(start, pos + 1));

        return pos;
    }
    
    protected int emitCode(XElement parent, String in, int start, MarkToken token) {
    	boolean dub = (token == MarkToken.CODE_DOUBLE);
    	
	    int a = start + (dub ? 2 : 1);
	    int b = this.findToken(in, a, token);
	    
	    if (b < start + 1)
	    	return -1;
	    	
        int pos = b + (dub ? 1 : 0);
        
        parent.with(W3.tag("code").withText(in.substring(a, b)));
        
        return pos;
	}

    protected int emitEm(XElement parent, String in, int start, MarkToken token) throws OperatingContextException {
		int b = this.findToken(in, start + 1, token);

		if (b > 0) {
			XElement em = W3.tag("em");
			
			parent.with(em);
			
			this.recursiveEmitLine(em, in.substring(start + 1, b), 0, token);

			return b;
		}

		return -1;
    }

    protected int emitSuper(XElement parent, String in, int start, MarkToken token) throws OperatingContextException {
		int b = this.findToken(in, start + 1, token);

		if (b > 0) {
			XElement em = W3.tag("sup");
			
			parent.with(em);
			
			this.recursiveEmitLine(em, in.substring(start + 1, b - 1), 0, token);
			//this.recursiveEmitLine(em, in.substring(1, in.length() - 2), 0, token);

			return b;
		}

		return -1;
    }

    protected int emitStrong(XElement parent, String in, int start, MarkToken token) throws OperatingContextException {
		int b = this.findToken(in, start + 2, token);

		if (b > 0) {
			XElement em = W3.tag("strong");
			
			parent.with(em);

			this.recursiveEmitLine(em, in.substring(start + 2, b), 0, token);
			//this.recursiveEmitLine(em, in.substring(start + 2, b - 4), 0, token);

			return b + 1;
		}

		return -1;
    }

    protected int emitStrike(XElement parent, String in, int start, MarkToken token) throws OperatingContextException {
		int b = this.findToken(in, start + 2, token);

		if (b > 0) {
			XElement em = W3.tag("s");
			
			parent.with(em);

			this.recursiveEmitLine(em, in.substring(start + 2, b), 0, token);
			//this.recursiveEmitLine(em, in.substring(2, in.length() - 4), 0, token);

			return b + 1;
		}

		return -1;
    }
    
    /*
     * Recursively scans through the given line, taking care of any markdown
     * stuff.
     */
    protected void recursiveEmitLine(XElement parent, String in, int start, MarkToken token) throws OperatingContextException {
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
                b = this.emitLink(parent, in, pos, mt);
                
                if(b > 0) 
                    pos = b;
                else 
                    parent.append(in.charAt(pos));
                
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
                    parent.append(in.charAt(pos));

                break;
            case EM_STAR:
            case EM_UNDERSCORE:
                b = this.emitEm(parent, in, pos, mt);
                
                if(b > 0) 
                    pos = b;
                else 
                    parent.append(in.charAt(pos));
                
                break;
            case STRONG_STAR:
            case STRONG_UNDERSCORE:
                b = this.emitStrong(parent, in, pos, mt);
                
                if(b > 0) 
                    pos = b;
                else 
                    parent.append(in.charAt(pos));
                
                break;
            case STRIKE:
                b = this.emitStrike(parent, in, pos, mt);
                
                if(b > 0) 
                    pos = b;
                else 
                    parent.append(in.charAt(pos));

                break;
            case SUPER:
                b = this.emitSuper(parent, in, pos, mt);
                
                if(b > 0) 
                    pos = b;
                else 
                    parent.append(in.charAt(pos));
                
                break;
            case CODE_SINGLE:
            case CODE_DOUBLE:
                b = this.emitCode(parent, in, pos, mt);
                
                if(b > 0) 
                    pos = b;
                else 
                    parent.append(in.charAt(pos));
            	
                break;
            case HTML:
                b = this.emitHtml(parent, in, pos);
                
                if(b > 0) 
                    pos = b;
                else 
                    parent.append("&lt;");

                break;
            case ENTITY:
                b = this.emitEntity(parent, in, pos, mt);
                
                if (b > 0) 
                    pos = b;
                else 
                    parent.append(in.charAt(pos));
                
                break;
            case X_COPY:
            	parent.appendEntity("&copy;");
                pos += 2;
                break;
            case X_REG:
                parent.appendEntity("&reg;");
                pos += 2;
                break;
            case X_TRADE:
                parent.appendEntity("&trade;");
                pos += 3;
                break;
            case X_NDASH:
                parent.appendEntity("&ndash;");
                pos++;
                break;
            case X_MDASH:
                parent.appendEntity("&mdash;");
                pos += 2;
                break;
            case X_HELLIP:
                parent.appendEntity("&hellip;");
                pos += 2;
                break;
            case X_LAQUO:
                parent.appendEntity("&laquo;");
                pos++;
                break;
            case X_RAQUO:
                parent.appendEntity("&raquo;");
                pos++;
                break;
            case X_RDQUO:
                parent.appendEntity("&rdquo;");
                break;
            case X_LDQUO:
                parent.appendEntity("&ldquo;");
                break;
            case ESCAPE:
                pos++;
                //$FALL-THROUGH$
            default:
            	char ch = in.charAt(pos);
            	
            	if (ch != '\n')
            		parent.append(ch);
            	else
					parent.with(ScriptHub.parseInstructions("<br />"));
             
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

    protected void emitMarkedLines(XElement parent, Line lines) throws OperatingContextException {
        StringBuilder in = new StringBuilder();
        Line line = lines;
        
        while(line != null) {
            if(!line.isEmpty)
                in.append(line.value.substring(line.leading, line.value.length() - line.trailing));
            
            if(line.next != null) 
                in.append("\n");
            
            line = line.next;
        }

        this.recursiveEmitLine(parent, in.toString(), 0, MarkToken.NONE);
    }

    protected void emitRawLines(XElement parent, Line lines) throws OperatingContextException {
        Line line = lines;
        
        if (! this.ctx.getConfig().getSafeMode()) {
            StringBuilder sb = new StringBuilder();
            
            while (line != null) {
                if(!line.isEmpty)
                    sb.append(line.value);

                sb.append('\n');
                
                line = line.next;
            }
            
            parent.with(new XText(false, sb.toString()));		// TODO check that safe really is escaped
        }
        else {
    		StringBuilder sb = new StringBuilder();

            while (line != null) {
                if (!line.isEmpty)
                    sb.append(line.value);
                
                sb.append("\n");

                line = line.next;
            }
	
			XElement res = ScriptHub.parseInstructions(sb);
            
            if (res != null)
            	parent.with(res);
            else
            	OperationContext.getAsTaskOrThrow().clearExitCode();
        }
    }

    protected void emitCodeLines(XElement parent, Line lines, String meta, boolean removeIndent) {
        Line line = lines;

		if (StringUtil.isNotEmpty(meta)) {
            parent.attr("class", "language-" + meta + " fenced");

            String[] tokens = meta.split("\\s+");

            for (int i = 0; i < tokens.length; i++) {
                if ("example".equals(tokens[i].trim()) && (parent instanceof Base))
                    parent.attr("dc:unresolvedvars", "true");
            }
        }
		
		StringBuilder sb = new StringBuilder();

        while (line != null) {
            if (! line.isEmpty)
                sb.append(removeIndent ? line.value.substring(4) : line.value);
            
            sb.append("\n");
            
            line = line.next;
        }
        
        parent.with(new XText(false, sb.toString()));
    }
    
    /*
     * interprets a plugin block into the StringBuilder.
     */
    protected void emitPluginLines(XElement parent, Line lines, String meta) {
		String idPlugin = meta;		
		String sparams = null;
		Map<String, String> params = null;
		int iow = meta.indexOf(' '); 
		
		if (iow != -1) {
			idPlugin = meta.substring(0, iow);
			sparams = meta.substring(iow+1);
			
			if (sparams != null) 
				params = parsePluginParams(sparams);
		}
		
		if (params == null) 
			params = new HashMap<String, String>();
		
        ArrayList<String> list = new ArrayList<String>();
        
        Line line = lines;
        
        while (line != null) {
            if (line.isEmpty)
                list.add("");
            else
                list.add(line.value);
            
            line = line.next;
        }

		Plugin plugin = plugins.get(idPlugin);
		
		if(plugin != null) 
			plugin.emit(this.ctx, parent, list, params);
    }
    
	static public Map<String, String> parsePluginParams(String s) {
		Map<String, String> params = new HashMap<String, String>();
	     Pattern p = Pattern.compile("(\\w+)=\"*((?<=\")[^\"]+(?=\")|([^\\s]+))\"*");

	     Matcher m = p.matcher(s);

	     while(m.find()){
	    	 params.put(m.group(1), m.group(2));
	     }	     
	     
	     return params;
	}
    
}
