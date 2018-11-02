package dcraft.web.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.UserContext;
import dcraft.locale.LocaleUtil;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.BooleanStruct;
import dcraft.util.StringUtil;
import dcraft.util.web.DateParser;
import dcraft.web.md.MarkdownUtil;
import dcraft.web.ui.inst.ICMSAware;
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

		// TODO try switching to
		// FeedUtil.bestMatch(catalog.selectFirst("EmailMessage"), defloc, defloc);

		XElement bestmatch = null;
		XElement firstmatch = null;

		for (int i = 0; i < source.getChildren().size(); i++) {
			XNode cn = source.getChild(i);
			
			if (! (cn instanceof XElement))
				continue;
			
			XElement cel = (XElement) cn;
			
			if (! "Tr".equals(cel.getName()))
				continue;
			
			String clocale = LocaleUtil.normalizeCode(StackUtil.stringFromElement(state, cel,"Locale"));
			
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
	
	static public void setEditBadges(InstructionWork state, XElement element) throws OperatingContextException {
		String badges = StackUtil.stringFromElement(state, element,"EditBadges");
		
		if (StringUtil.isEmpty(badges))
			return;
		
		String[] blist = badges.split(",");
		ListStruct bvar = ListStruct.list();
		
		for (int i = 0; i < blist.length; i++) {
			bvar.with(blist[i].trim());
		}
		
		StackUtil.addVariable(state, "_CMSEditBadges", bvar);
		
		if (element instanceof ICMSAware)
			((ICMSAware) element).canonicalize();
	}
	
	static public boolean markIfEditable(InstructionWork state, XElement element, String cmstype) throws OperatingContextException {
		UIUtil.setEditBadges(state, element);
		
		boolean editable = UIUtil.canEdit(state, element);
		
		if (editable)
			element.withAttribute("data-cms-type", cmstype);

		return editable;
	}
	
	static public boolean canEdit(InstructionWork state, XElement element) throws OperatingContextException {
		Struct editable = StackUtil.resolveReference(state, "$_CMSEditable", true);
		
		if (! Struct.objectToBooleanOrFalse(editable))
			return false;
		
		if (element.hasEmptyAttribute("id"))
			return false;
		
		UserContext userContext = OperationContext.getOrThrow().getUserContext();
		
		ListStruct editBadges = Struct.objectToList(StackUtil.resolveReference(state, "$_CMSEditBadges", true));

		if (editBadges == null)
			return userContext.isTagged("Admin", "Editor");
		
		for (int i = 0; i < editBadges.size(); i++) {
			if (userContext.isTagged(editBadges.getItemAsString(i))) {
				if (element instanceof ICMSAware)
					return ((ICMSAware) element).canEdit(state);

				return true;
			}
		}

		return false;
	}
}
