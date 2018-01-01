package dcraft.cms.feed.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeComposite;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.script.ScriptHub;
import dcraft.struct.CompositeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.tenant.Site;
import dcraft.util.IOUtil;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.xml.XElement;
import dcraft.xml.XmlReader;

public class FeedInfo {
	public static FeedInfo recordToInfo(RecordStruct rec) throws OperatingContextException {
		String channel = rec.getFieldAsString("Channel");
		String path = rec.getFieldAsString("Path");
		
		return FeedInfo.buildInfo(channel, path);
	}
	
	// path without channel
	public static FeedInfo buildInfo(String channel, String path) throws OperatingContextException {
		if (StringUtil.isEmpty(channel) || StringUtil.isEmpty(path))
			return null;
		
		XElement channelDef = FeedIndexer.findFeed(channel);
		
		// if channelDef is null then it does not exist
		if (channelDef == null)
			return null;
		
		FeedInfo fi = new FeedInfo();
		
		fi.channel = channel;
		fi.path = path;
		fi.channelDef = channelDef;
		
		fi.init();
		
		return fi;
	}
	
	protected String channel = null;
	protected String path = null;
	
	protected XElement channelDef = null;
	protected XElement draftDcfContent = null;
	protected XElement pubDcfContent = null;
	protected Path pubpath = null;
	protected Path prepath = null;

	// for this work correctly you need to set channel and path first
	public void init() throws OperatingContextException {
		if (this.channelDef == null) 
			return;
		
		Site site = OperationContext.getOrThrow().getSite();
		
		//this.innerpath = "/" + site.getAlias() + this.feedpath;		
		
		this.prepath = site.resolvePath("feed-preview/" + this.channel + this.path + ".dcf.xml").toAbsolutePath().normalize();
		this.pubpath = site.resolvePath("feed/" + this.channel + this.path + ".dcf.xml").toAbsolutePath().normalize();
	}
	
	public String getChannel() {
		return this.channel;
	}
	
	public String getUrlPath() {
		if ("pages".equals(this.channel))
			return this.path;
		
		// TODO if this channel has an alternative Url path, lookup here
		
		return "/" + this.channel + this.path;
	}
	
	public XElement getChannelDef() {
		return this.channelDef;
	}
	
	public XElement getDraftDcfContent() {
		if ((this.draftDcfContent == null) && (Files.exists(this.prepath)))
			this.draftDcfContent = XmlReader.loadFile(this.prepath, false, true);
		
		return this.draftDcfContent;
	}
	
	public XElement getPubDcfContent() {
		if ((this.pubDcfContent == null) && (Files.exists(this.pubpath)))
			this.pubDcfContent = XmlReader.loadFile(this.pubpath, false, true);
		
		return this.pubDcfContent;
	}
	
	public Path getPubpath() {
		return this.pubpath;
	}
	
	public Path getPrepath() {
		return this.prepath;
	}
	
	public boolean exists() {
		return Files.exists(this.pubpath) || Files.exists(this.prepath);
	}

	public RecordStruct getDetails() throws OperatingContextException {
		// work through the adapters
		FeedAdapter pubfeed = this.getPubAdapter();
		FeedAdapter prefeed = this.getPreAdapter();
		
		if ((pubfeed == null) && (prefeed == null)) 
			return null;
		
		XElement pubxml = (pubfeed != null) ? pubfeed.getXml() : null;
		XElement prexml = (prefeed != null) ? prefeed.getXml() : null;

		// if no file is present then delete record for feed
		if ((pubxml == null) && (prexml == null)) 
			return null;
		
		// if at least one xml file then update/add a record for the feed
		
		RecordStruct feed = new RecordStruct()
			.with("Site", OperationContext.getOrThrow().getSite().getAlias())
			.with("Channel", this.channel)
			.with("Path", this.path);
		
		// the "edit" authorization, not the "view" auth
		String authtags = (pubfeed != null) ? pubfeed.getAttribute("AuthTags") : prefeed.getAttribute("AuthTags");
		
		if (StringUtil.isEmpty(authtags))
			feed.with("AuthorizationTags", ListStruct.list());
		else
			feed.with("AuthorizationTags", ListStruct.list((Object[]) authtags.split(",")));

		if (pubxml != null) {
			ListStruct ctags = new ListStruct();
			
			for (XElement tag : pubxml.selectAll("Tag")) {
				String alias = tag.getAttribute("Alias");
				
				if (StringUtil.isNotEmpty(alias))
					ctags.with(alias);
			}
			
			feed.with("ContentTags", ctags);
		}
		else if (prexml != null) {
			ListStruct ctags = new ListStruct();
			
			for (XElement tag : prexml.selectAll("Tag")) {
				String alias = tag.getAttribute("Alias");
				
				if (StringUtil.isNotEmpty(alias))
					ctags.with(alias);
			}
			
			feed.with("ContentTags", ctags);
		}
		
		if (pubxml != null) {
			// public fields
			
			String primelocale = pubxml.getAttribute("Locale"); 
			
			if (StringUtil.isEmpty(primelocale))
				primelocale = OperationContext.getOrThrow().getLocaleDefinition().getName();
			
			feed.with("Locale", primelocale);
	
			ListStruct pubfields = new ListStruct();
			feed.with("Fields", pubfields);
			
			for (XElement fld : pubxml.selectAll("Field")) 
				pubfields.with(new RecordStruct()
					.with("Name", fld.getAttribute("Name"))
					.with("Locale", fld.getAttribute("Locale", primelocale))		// prime locale can be override for field, though it means little besides adding to search info
					.with("Value", fld.getValue())
				);
			
			ListStruct pubparts = new ListStruct();
			feed.with("PartContent", pubparts);
			
			for (XElement fld : pubxml.selectAll("PagePart"))
				pubparts.with(new RecordStruct()
					.with("Name", fld.getAttribute("For"))
					.with("Format", fld.getAttribute("Format", "md"))
					.with("Locale", fld.getAttribute("Locale", primelocale))		// prime locale can be override for specific part
				);
		}
		
		if (prexml != null) {
			// preview fields
			
			String primelocale = prexml.getAttribute("Locale"); 
			
			if (StringUtil.isEmpty(primelocale))
				primelocale = OperationContext.getOrThrow().getLocaleDefinition().getName();

			if (! feed.hasField("Locale"))
				feed.with("Locale", primelocale);

			ListStruct prefields = new ListStruct();
			feed.with("PreviewFields", prefields);
			
			for (XElement fld : prexml.selectAll("Field")) 
				prefields.with(new RecordStruct()
					.with("Name", fld.getAttribute("Name"))
					.with("Locale", fld.getAttribute("Locale", primelocale))		// prime locale can be override for field, though it means little besides adding to search info
					.with("Value", fld.getValue())
				);
			
			ListStruct preparts = new ListStruct();
			feed.with("PreviewPartContent", preparts);
			
			for (XElement fld : prexml.selectAll("PagePart")) 
				preparts.with(new RecordStruct()
					.with("Name", fld.getAttribute("For"))
					.with("Format", fld.getAttribute("Format", "md"))
					.with("Locale", fld.getAttribute("Locale", primelocale))		// prime locale can be override for specific part
				);
		}
		
		return feed;
	}

	public FeedAdapter getPreAdapter() {
		FeedAdapter adapt = new FeedAdapter();
		adapt.init(this.channel, this.path, this.prepath);		// load direct, not cache - cache may not have updated yet
		
		return adapt;
	}

	public FeedAdapter getPubAdapter() {
		FeedAdapter adapt = new FeedAdapter();
		adapt.init(this.channel, this.path, this.pubpath);		// load direct, not cache - cache may not have updated yet
		
		return adapt;
	}
	
	public FeedAdapter getAdapter(boolean draft) {
		if (draft) {
			FeedAdapter adpt = this.getPreAdapter();
			
			if ((adpt != null) && (adpt.getXml() != null))
				return adpt;
		}
		
		FeedAdapter adpt = this.getPubAdapter();
		
		if ((adpt != null) && (adpt.getXml() != null))
			return adpt;
		
		return null;
	}
	
	// this is not required, you may go right to saveDraftFile
	// dcui = only if a Page
	/*
	public void initDraftFile(String locale, String title, String dcui, FuncCallback<CompositeStruct> op) {
		if (StringUtil.isEmpty(locale))
			locale = OperationContext.get().getSite().getDefaultLocale();		
		
		if ("pages".equals(this.getChannel()) && StringUtil.isNotEmpty(dcui)) {
			SiteInfo site = OperationContext.get().getSite();
			
			// don't go to www-preview at first, www-preview would only be used by a developer showing an altered page
			// for first time save, it makes sense to have the dcui file in www
			Path uisrcpath = site.resolvePath("www" + this.path + ".html");		
			
			try {
				Files.createDirectories(uisrcpath.getParent());
				IOUtil.saveEntireFile(uisrcpath, dcui);
			}
			catch (Exception x) {
				op.error("Unable to add dcui file: " + x);
				op.complete();
				return;
			}
		}

		try {
			Files.createDirectories(this.getPrepath().getParent());
		}
		catch (Exception x) {
			op.error("Unable to create draft folder: " + x);
			op.complete();
			return;
		}
		
		XElement root = this.getFeedTemplate(title, locale);
		
		// TODO clear the CacheFile index for this path so that we get up to date entries when importing
		IOUtil.saveEntireFile(this.getPrepath(), root.toString(true));
		
		this.updateDb(op);
	}	
	*/
	
	protected XElement getFeedTemplate(String title, String locale) throws OperatingContextException {
		return new XElement("dcf")
				.withAttribute("Locale", locale)
				.with(new XElement("Field")
					.withAttribute("Value", title)
					.withAttribute("Name", "Title")
				)
				.with(new XElement("Field")
					.withAttribute("Value", OperationContext.getOrThrow().getUserContext().getFirstName()
							+ " " + OperationContext.getOrThrow().getUserContext().getLastName())
					.withAttribute("Name", "AuthorName")
				)
				.with(new XElement("Field")
					.withAttribute("Value", OperationContext.getOrThrow().getUserContext().getUsername())
					.withAttribute("Name", "AuthorUsername")
				)
				.with(new XElement("Field")
					.withAttribute("Value", TimeUtil.stampFmt.format(TimeUtil.now()))
					.withAttribute("Name", "Created")
				);
	}
	
	// dcf = content for the dcf file
	// updates = list of records (Name, Content) to write out
	// deletes = list of filenames to remove
	public void saveFile(boolean publish, ListStruct fields, ListStruct parts, ListStruct tags, OperationOutcomeEmpty op) throws OperatingContextException {
		OperationContext ctx = OperationContext.getOrThrow();
		String locale = ctx.getLocale();
		
		if (fields == null)
			fields = new ListStruct();
		
		if (parts == null)
			parts = new ListStruct();
		
		Path savepath = this.getPrepath();
		
		try {
			Files.createDirectories(savepath.getParent());
		}
		catch (Exception x) {
			Logger.error("Unable to create draft folder: " + x);
			op.returnEmpty();
			return;
		}
		
		FeedAdapter ad = this.getPreAdapter();
		
		if (!ad.isFound())
			ad = this.getPubAdapter();
		
		if (!ad.isFound()) {
			//op.error("Unable to locate feed file, cannot alter.");
			//op.complete();
			//return;
			ad.xml = this.getFeedTemplate("[Unknown]", locale);			
		}
		
		// default published, can be overridden in the SetFields collection
		if (publish) {
			XElement mr = ad.getDefaultField("Published");
			
			if (mr == null) 
				ad.setField(locale, "Published", LocalDate.now().toString());
		}
		
		for (Struct fs : fields.items()) {
			RecordStruct frec = (RecordStruct) fs;
			
			if (frec.hasField("Value"))
				ad.setField(locale, frec.getFieldAsString("Name"), frec.getFieldAsString("Value"));
			else
				ad.removeField(locale, frec.getFieldAsString("Name"));
		}
		
		for (Struct ps : parts.items()) {
			RecordStruct frec = (RecordStruct) ps;
			
			XElement parent = ad.xml.findParentOfId(frec.getFieldAsString("Id"));
			
			if (parent == null)
				continue;
			
			XElement match = parent.findId(frec.getFieldAsString("Id"));
			
			if (frec.hasField("Value")) {
				XElement newval = ScriptHub.parseInstructions(frec.getFieldAsString("Value"));
				
				match.replace(newval);
			}
			else {
				parent.remove(match);
			}
		}
		
		if (tags != null) {
			ad.clearTags();
			
			for (Struct ts : tags.items())
				ad.addTag(ts.toString());
		}
		
		IOUtil.saveEntireFile(savepath, ad.getXml().toString(true));

		if (publish) 
			this.publicizeFile(op);
		else 
			this.updateDb(op);
	}
	
	// dcf = content for the dcf file
	// updates = list of records (Name, Content) to write out
	// deletes = list of filenames to remove
	public void saveFile(boolean draft, XElement dcf, ListStruct updates, ListStruct deletes, OperationOutcomeEmpty op) throws OperatingContextException {
		Path savepath = this.getPrepath();
		
		try {
			Files.createDirectories(savepath.getParent());
		}
		catch (Exception x) {
			Logger.error("Unable to create draft folder: " + x);
			op.returnEmpty();
			return;
		}
		
		String locale = dcf.getAttribute("Locale");
		
		if (StringUtil.isEmpty(locale))
			dcf.setAttribute("Locale", OperationContext.getOrThrow().getLocale());		// TODO site locale?
		
		try {
			if (deletes != null)
				for (Struct df : deletes.items())
					// TODO clear the CacheFile index for this path so that we get up to date entries when importing
					Files.deleteIfExists(savepath.resolveSibling(df.toString()));
				
			if (updates != null)
				for (Struct uf : updates.items()) {
					RecordStruct urec = (RecordStruct) uf;
	
					// TODO clear the CacheFile index for this path so that we get up to date entries when importing
					IOUtil.saveEntireFile(savepath.resolveSibling(urec.getFieldAsString("Name")), urec.getFieldAsString("Content"));
				}
			
			if (dcf != null)
				// TODO clear the CacheFile index for this path so that we get up to date entries when importing
				IOUtil.saveEntireFile(savepath, dcf.toString(true));
			
			// cleanup any draft files, we skipped over them
			if (draft) 
				this.updateDb(op);
			else
				this.publicizeFile(op);
			
		}
		catch (Exception x) {
			Logger.error("Unable to update feed: " + x);
			op.returnEmpty();
		}
	}
	
	public void publicizeFile(OperationOutcomeEmpty op) throws OperatingContextException {
		// if no preview available then nothing we can do here
		if (Files.notExists(this.getPrepath())) {
			op.returnEmpty();
			return;
		}

		try {
			Files.createDirectories(this.getPubpath().getParent());
		}
		catch (Exception x) {
			Logger.error("Unable to create publish folder: " + x);
			op.returnEmpty();
			return;
		}
		
		// finally move the feed file itself
		try {
			Files.move(this.getPrepath(), this.getPubpath(), StandardCopyOption.REPLACE_EXISTING);
			
			this.updateDb(op);
		}
		catch (Exception x) {
			Logger.error("Unable to move preview file: " + this.getPrepath() +  " : " + x);
			op.returnEmpty();
		}
	}

	public void deleteFile(DeleteMode mode, OperationOutcomeEmpty op) throws OperatingContextException {
		for (int i = 0; i < 2; i++) {
			boolean draft = (i == 0);
			
			if (draft && (mode == DeleteMode.Published))
				continue;
			
			if (!draft && (mode == DeleteMode.Draft))
				continue;
			
			Path fpath = draft ? this.getPrepath() : this.getPubpath();
			
			// if no dcf file available then nothing we can do 
			if (Files.notExists(fpath)) 
				continue;
			
			// finally move the feed file itself
			try {
				Files.deleteIfExists(fpath);
			}
			catch (Exception x) {
				Logger.error("Unable to delete feed file: " + fpath +  " : " + x);
			}
			
			// delete Page definitions...
			if ("pages".equals(channel)) {
				Site siteinfo = OperationContext.getOrThrow().getSite();
				
				Path srcpath = draft 
						? siteinfo.resolvePath("www-preview/" + this.path + ".dcui.xml")
						: siteinfo.resolvePath("www/" + this.path + ".dcui.xml");
				
				try {
					Files.deleteIfExists(srcpath);
				}
				catch  (Exception x) {
				}
			}			
		}
		
		this.deleteDb(op);
	}

	public void deleteDb(OperationOutcomeEmpty cb) throws OperatingContextException {
		DataStore.deleteFeed("/" + OperationContext.getOrThrow().getSite().getAlias() + "/" + this.channel + this.path, cb);
	}

	public void updateDb(OperationOutcomeEmpty cb) throws OperatingContextException {
		RecordStruct feed = this.getDetails();

		if (feed == null) {
			this.deleteDb(cb);
			return;
		}
		
		DataStore.updateFeed(feed, cb);
	}
}
