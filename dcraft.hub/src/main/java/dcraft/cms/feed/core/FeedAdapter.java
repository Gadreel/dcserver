package dcraft.cms.feed.core;

import java.nio.file.Files;
import java.nio.file.Path;

import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.locale.LocaleResource;
import dcraft.log.Logger;
import dcraft.script.ScriptHub;
import dcraft.util.IOUtil;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class FeedAdapter {
	static public FeedAdapter from(String feed, String path, String view) throws OperatingContextException {
		XElement chan = FeedIndexer.findFeed(feed);
		
		if (chan == null) 
			return null;
		
		Path cfile = OperationContext.getOrThrow().getSite().findSectionFile("feed", "/" + feed + path + ".dcf.xml", view);
		
		if (cfile == null)
			return null;
		
		FeedAdapter adapt = new FeedAdapter();
		
		adapt.init(feed, path, cfile);
		
		return adapt; 
	}
	
	/* TODO review
	static public FeedAdapter from(RecordStruct data) throws OperatingContextException {
		CommonPath cpath = new CommonPath(data.getFieldAsString("Path"));
		
		FeedAdapter adapt = new FeedAdapter();

		adapt.feed = cpath.getName(1);		// site alias is 0
		adapt.path = cpath.subpath(2).toString();
		adapt.xml = new XElement("dcf");
		
		String deflocale = OperationContext.getOrThrow().getLocale();
		
		adapt.xml.withAttribute("Locale", deflocale);
		
		for (Struct fs : data.getFieldAsList("Fields").items()) {
			RecordStruct frec = (RecordStruct) fs;
			
			String sub = frec.getFieldAsString("SubId");
			String fdata = frec.getFieldAsString("Data");
			
			if (StringUtil.isEmpty(sub) || StringUtil.isEmpty(fdata))
				continue;
			
			int dpos = sub.lastIndexOf('.');
			
			adapt.setField(sub.substring(dpos + 1), sub.substring(0, dpos), fdata);
		}

		return adapt; 
	}
	*/
	
	protected String feed = null;
	protected String path = null;
	protected Path filepath = null;
	protected XElement xml = null;
	
	public String getFeed() {
		return this.feed;
	}
	
	public String getPath() {
		return this.path;
	}
	
	public Path getFilePath() {
		return this.filepath;
	}
	
	public XElement getXml() {
		return this.xml;
	}
	
	// best not to use CacheFile always, not if from feed save/feed delete
	public void init(String feed, String path, Path filepath) {
		if (filepath == null)
			return;
		
		this.feed = feed;
		this.path = path;
		this.filepath = filepath;
		
		if (Files.notExists(filepath))
			return;
		
		this.xml = ScriptHub.parseInstructions(IOUtil.readEntireFile(filepath));
		
		if (this.xml == null)
			Logger.error("Bad feed file - " + this.feed + " | " + this.path);
	}
	
	public boolean isFound() {
		return (this.xml != null);
	}

	public XElement getMeta() {
		if (this.xml == null)
			return null;
		
		return this.xml.find("Mata");
	}
	
	public String getAttribute(String name) {
		XElement meta = this.getMeta();
		
		if ((meta == null) || StringUtil.isEmpty(name))
			return null;
		
		return meta.getAttribute(name);
	}
	
	public String getDefaultFieldValue(String name) {
		XElement fel = this.getDefaultField(name);

		return (fel != null) ? fel.getValue() : null;
	}
	
	public XElement getDefaultField(String name) {
		XElement meta = this.getMeta();
		
		if ((meta == null) || StringUtil.isEmpty(name))
			return null;
		
		// provide the value for the `default` locale of the feed 
		String deflocale = meta.getAttribute("Locale");
		
		for (XElement fel : meta.selectAll("Field")) {
			if (name.equals(fel.getAttribute("Name"))) {
				if (!fel.hasAttribute("Locale"))
					return fel;
					
				if ((deflocale != null) && deflocale.equals(fel.getAttribute("Locale")))
					return fel;
			}
		}
		
		return null;
	}
	
	public FieldMatchResult bestMatch(String attr, String match) throws OperatingContextException {
		XElement meta = this.getMeta();
		
		if ((meta == null) || StringUtil.isEmpty(attr) || StringUtil.isEmpty(match))
			return null;

		int highest = Integer.MAX_VALUE;
		FieldMatchResult best = new FieldMatchResult();
		
		String deflocale = meta.getAttribute("Locale", OperationContext.getOrThrow().getLocale());
		
		LocaleResource lres = ResourceHub.getResources().getLocale();
		
		for (XElement fel : meta.selectAll("Field")) {
			if (! match.equals(fel.getAttribute(attr)))
					continue;
			
			String flocale = fel.getAttribute("Locale", deflocale);
		
			int arate = lres.rateLocale(flocale);
			
			if ((arate == -1) && ! deflocale.equals(flocale))
				break;
			
			// if all else fails use default
			if ((arate == -1))
				arate = 100;
			
			if (arate >= highest)
				continue;
				
			best.el = fel;
			best.localename = flocale;
			highest = arate;
		}
		
		if (highest == Integer.MAX_VALUE)
			return null;
		
		best.locale = lres.getLocaleDefinition(best.localename);
		
		return best;
	}
	
	public String getFirstField(String... names) throws OperatingContextException {
		for (String n : names) {
			String v = this.getField(n);
			
			if (v != null)
				return v;
		}
		
		return null;
	}
	
	public String getField(String name) throws OperatingContextException {
		XElement meta = this.getMeta();
		
		if ((meta == null) || StringUtil.isEmpty(name))
			return null;
		
		FieldMatchResult mr = this.bestMatch("Name", name);
		
		if (mr != null)
			return mr.el.getValue();
		
		return null;
	}
	
	public String getField(String locale, String name) throws OperatingContextException {
		XElement meta = this.getMeta();
		
		if ((meta == null) || StringUtil.isEmpty(name))
			return null;

		if (StringUtil.isEmpty(locale))
			locale = OperationContext.getOrThrow().getLocale();
		
		// provide the value for the `default` locale of the feed 
		String deflocale = meta.getAttribute("Locale", OperationContext.getOrThrow().getLocale());

		// if matches default locale then Field goes in top level elements
		for (XElement fel : meta.selectAll("Field")) {
			if (! name.equals(fel.getAttribute("Name"))) 
				continue;
			
			if (locale.equals(deflocale) && ! fel.hasAttribute("Locale")) 
				return fel.getValue();
			
			if (locale.equals(fel.getAttribute("Locale"))) 
				return fel.getValue();
		}
				
		return null;
	}
	
	public void setField(String locale, String name, String value) throws OperatingContextException {
		XElement meta = this.getMeta();
		
		if ((meta == null) || StringUtil.isEmpty(name))
			return;
		
		// provide the value for the `default` locale of the feed 
		String deflocale = meta.getAttribute("Locale", OperationContext.getOrThrow().getLocale());
		
		// TODO support other fields based on feed
		// special handling for some fields - always at top level
		if ("Published".equals(name) || "AuthorUsername".equals(name) || "AuthorName".equals(name) || "Created".equals(name))
			locale = deflocale;

		// if matches default locale then Field goes in top level elements
		for (XElement fel : meta.selectAll("Field")) {
			if (! name.equals(fel.getAttribute("Name"))) 
				continue;
			
			if (locale.equals(deflocale) && ! fel.hasAttribute("Locale")) {
				fel.setValue(value);
				return;
			}
			
			if (locale.equals(fel.getAttribute("Locale"))) {
				fel.setValue(value);
				return;
			}
		}
		
		XElement fel = new XElement("Field")
				.withAttribute("Name", name);
		
		if (! locale.equals(deflocale))
			fel.withAttribute("Locale", locale);
		
		fel.setValue(value);
				
		meta.with(fel);
	}
	
	public void removeField(String locale, String name) throws OperatingContextException {
		XElement meta = this.getMeta();
		
		if ((meta == null) || StringUtil.isEmpty(name))
			return;

		if (StringUtil.isEmpty(locale))
			locale = OperationContext.getOrThrow().getLocale();
		
		// provide the value for the `default` locale of the feed 
		String deflocale = meta.getAttribute("Locale", OperationContext.getOrThrow().getLocale());

		// if matches default locale then Field goes in top level elements
		for (XElement fel : meta.selectAll("Field")) {
			if (! name.equals(fel.getAttribute("Name"))) 
				continue;
			
			if (locale.equals(deflocale) && ! fel.hasAttribute("Locale")) {
				meta.remove(fel);
				return;
			}
			
			if (locale.equals(fel.getAttribute("Locale"))) {
				meta.remove(fel);
				return;
			}
		}
	}
	
	public void clearTags() {
		XElement meta = this.getMeta();
		
		if (meta == null)
			return;
		
		for (XElement t : meta.selectAll("Tag"))
			meta.remove(t);
	}
	
	public void addTag(String tag) {
		XElement meta = this.getMeta();
		
		if (meta == null)
			return;
		
		meta.with(new XElement("Tag").withAttribute("Alias", tag));
	}
	
	public String getTags() {
		XElement meta = this.getMeta();
		
		if (meta == null)
			return "";
		
		StringBuilder sb = new StringBuilder();
		
		for (XElement t : meta.selectAll("Tag")) {
			if (sb.length() > 0)
				sb.append(", ");
			
			sb.append(t.getAttribute("Alias"));
		}
		
		return sb.toString();
	}
}
