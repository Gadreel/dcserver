package dcraft.web.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.struct.RecordStruct;
import dcraft.util.StringUtil;
import dcraft.util.web.DateParser;
import dcraft.web.md.MarkdownUtil;
import dcraft.xml.XElement;
import dcraft.xml.XNode;

public class UIUtil {
	// @[a-zA-Z0-9_\\-,:/]+@
	//static public final Pattern macropattern = Pattern.compile("@\\S+?@", Pattern.MULTILINE);
	static public final Pattern linkpattern = Pattern.compile("\\(?\\b(http://|https://|www[.])[-A-Za-z0-9+&@#/%?=~_()|!:,.;]*[-A-Za-z0-9+&@#/%=~_()|]", Pattern.MULTILINE);

	static public boolean urlLooksLocal(String url) {
		if (StringUtil.isEmpty(url))
			return false;
		
		if (url.startsWith("https://") || url.startsWith("http://") || url.startsWith("//"))
			return false;
		
		return true;
	}
	
	static public String regularTextToMarkdown(String text) {
		Set<String> links = UIUtil.findAllLinks(text);
		
		for (String link : links) {
			if (link.startsWith("https://") || link.startsWith("http://"))
				text = text.replace(link, "[" + link + "](" + link + ")");
			else
				text = text.replace(link, "[" + link + "](http://" + link + ")");
		}
		
		return text;
	}
	
	// Pull all links from the body for easy retrieval
	static public Set<String> findAllLinks(String text) {
		Set<String> links = new HashSet<>();
		
		Matcher m = UIUtil.linkpattern.matcher(text);
		
		while(m.find()) {
			String urlStr = m.group();
			
			if (urlStr.startsWith("(") && urlStr.endsWith(")"))
				urlStr = urlStr.substring(1, urlStr.length() - 1);

			links.add(urlStr);
		}
		
		return links;
	}
	
	static public XElement translate(InstructionWork state, XElement source, boolean issafe) throws OperatingContextException {
		String locale = OperationContext.getOrThrow().getLocale();
		String deflocale = OperationContext.getOrThrow().getSite().getResources().getLocale().getDefaultLocale();
		
		XElement bestmatch = null;
		XElement firstmatch = null;

		for (int i = 0; i < source.getChildren().size(); i++) {
			XNode cn = source.getChild(i);
			
			if (! (cn instanceof XElement))
				continue;;
			
			XElement cel = (XElement) cn;
			
			if (! "Tr".equals(cel.getName()))
				continue;
			
			String clocale = StackUtil.stringFromElement(state, cel,"Locale");
			
			// match current locale then use it
			if (locale.equals(clocale)) {
				bestmatch = cel;
				break;
			}
			
			// no locale suggests default
			if (deflocale.equals(clocale) || StringUtil.isEmpty(clocale))
				bestmatch = cel;
			
			if (firstmatch == null)
				firstmatch = cel;
		}
		
		if (bestmatch == null)
			return bestmatch = firstmatch;

		if (bestmatch == null)
			return null;
		
		String content = StackUtil.resolveValueToString(state, bestmatch.getText());
		
		XElement root = MarkdownUtil.process(content, issafe);
		
		if (root == null) {
			Logger.warn("inline md error: ");
			OperationContext.getAsTaskOrThrow().clearExitCode();
		}
		
		return root;
	}
	
	static public long getDateHeader(RecordStruct headers, String name) {
		String value = headers.getFieldAsString(name);
		
		if (StringUtil.isEmpty(value))
			return -1;
		
		DateParser parser = new DateParser();
		return parser.convert(value);
	}
	
	// TODO redundant with FeedInfo - cleanup
	// TODO include state
	/*
	public String getMeta(XElement doc, String name) throws OperatingContextException {
		String locale = OperationContext.getOrThrow().getLocale();
		
		// TODO refine, also check lang
		
		for (XNode n : doc.getChildren()) {
			if (! (n instanceof XElement))
				continue;;
				
			XElement el = (XElement) n;
			
			if (! "Meta".equals(el.getName()))
				continue;
			
			if (! name.equals(el.getAttribute("Name")))
				continue;
			
			for (XNode t : el.getChildren()) {
				if (! (t instanceof XElement))
					continue;;
				
				XElement tel = (XElement) t;
				
				if (! "Tr".equals(tel.getName()))
					continue;
				
				if (! locale.equals(tel.getAttribute("Locale")))
					continue;
				
				return tel.getAttribute("Value");
			}
			
			return el.getAttribute("Value");
		}
		
		return null;
	}
	*/
}
