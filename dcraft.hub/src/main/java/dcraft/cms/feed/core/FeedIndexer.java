package dcraft.cms.feed.core;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.log.Logger;
import dcraft.tenant.Site;
import dcraft.util.StringUtil;
import dcraft.util.cb.CountDownCallback;
import dcraft.xml.XElement;

public class FeedIndexer {
	public static XElement findFeed(String feed) throws OperatingContextException {
		if (StringUtil.isEmpty(feed))
			return null;
		
		Site siteinfo = OperationContext.getOrThrow().getSite();
		
		XElement feedel = siteinfo.getFeeds();
		
		if (feedel == null)
			return null;
		
		for (XElement chan : feedel.selectAll("Feed")) {
			String calias = chan.getAttribute("Alias");
			
			if (calias == null)
				calias = chan.getAttribute("Name");
			
			if (! calias.equals(feed))
				continue;
			
			return chan;
		}
		
		return null;
	}
	
	public static List<XElement> findFeeds() throws OperatingContextException {
		List<XElement> list = new ArrayList<>();
		
		Site siteinfo = OperationContext.getOrThrow().getSite();
		
		XElement feed = siteinfo.getFeeds();
		
		if (feed == null) 
			return list;
		
		for (XElement chan : feed.selectAll("Feed")) {
			String calias = chan.getAttribute("Alias");
			
			if (calias == null)
				calias = chan.getAttribute("Name");
			
			list.add(chan);
		}
		
		return list;
	}
	
	protected Map<String, FeedInfo> feedpaths = new HashMap<>();
	
	/*
	 * run collectTenant first
	 */
	public void importSite(OperationOutcomeEmpty op) throws OperatingContextException {
		CountDownCallback cd = new CountDownCallback(this.feedpaths.size() + 1, new OperationOutcomeEmpty() {
			@Override
			public void callback() throws OperatingContextException {
				// =============== DONE ==============
				if (op.hasErrors())
					Logger.info("Website import completed with errors!");
				else
					Logger.info("Website import completed successfully");
				
				op.returnEmpty();
			}
		});
		
		for (FeedInfo fi : this.feedpaths.values())
			fi.updateDb(new OperationOutcomeEmpty() {
				@Override
				public void callback() {
					cd.countDown();
				}
			});
		
		cd.countDown();
	}
	
	public void collectSite(CollectContext cctx) throws OperatingContextException {
		Logger.info("Importing web content for domain: " + OperationContext.getOrThrow().getSite().getTitle()
				+ " site: " + OperationContext.getOrThrow().getSite().getAlias());
		
		// TODO improve collection process
		
		for (XElement chan : FeedIndexer.findFeeds())
			this.collectFeed(cctx, chan);
		
		Logger.info("File count collected for import: " + this.feedpaths.size());
	}
	
	public void collectFeed(CollectContext cctx, XElement chan) throws OperatingContextException {
		String alias = chan.getAttribute("Alias");
		
		if (alias == null)
			alias = chan.getAttribute("Name");
		
		// pages and blocks do not index the same way for public
		if (cctx.isForSitemap() && ("Pages".equals(alias) || !chan.getAttribute("AuthTags", "Guest").contains("Guest")))
			return;
		
		if (cctx.isForIndex() && "true".equals(chan.getAttribute("DisableIndex", "False").toLowerCase()))
			return;
		
		Logger.info("Importing site content for: " + OperationContext.getOrThrow().getSite().getAlias() + " > " + alias);
		
		this.collectArea("feed", alias, false);
		
		if (!cctx.isForSitemap())
			this.collectArea("feed", alias, true);
	}
	
	public void collectArea(String area, String feed, boolean preview) throws OperatingContextException {
		Site siteinfo = OperationContext.getOrThrow().getSite();

		String wwwpathf1 = preview ? area +  "-preview/" + feed : area +  "/" + feed;
		
		Path wwwsrc1 = siteinfo.resolvePath(wwwpathf1).toAbsolutePath().normalize();
		
		if (!Files.exists(wwwsrc1)) 
			return;
		
		try {
			Files.walkFileTree(wwwsrc1, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path sfile, BasicFileAttributes attrs) {
					Path relpath = wwwsrc1.relativize(sfile);
					
					String fpath = "/" + relpath.toString().replace('\\', '/');

					// only collect dcf files
					if (!fpath.endsWith(".dcf.xml")) 
						return FileVisitResult.CONTINUE;
					
					// TODO if this is a Page feed then confirm that there is a corresponding .dcui.xml file - if not skip it
					
					fpath = fpath.substring(0, fpath.length() - 8);
					
					fpath = "/" + feed + fpath;
					
					Logger.debug("Considering file " + feed + " > " + fpath);

					// skip if already in the collect list
					if (FeedIndexer.this.feedpaths.containsKey(fpath)) 
						return FileVisitResult.CONTINUE;
					
					// add to the list
					if (preview) 
						Logger.info("Adding preview only " + feed + " > " + fpath);
					else 
						Logger.info("Adding published " + feed + " > " + fpath);
						
					try {
						FeedInfo fi = FeedInfo.buildInfo(feed, fpath);
						
						FeedIndexer.this.feedpaths.put(fpath, fi);
					}
					catch (OperatingContextException x) {
						Logger.error("Bad context: " + x);
					}
					
					return FileVisitResult.CONTINUE;
				}
			});
		}
		catch (IOException x) {
			Logger.error("Error indexing feed " + area + ": " + feed + " : " + x);
		}
	}

	public void addToSitemap(String indexurl, XElement smel, List<String> altlocales) throws OperatingContextException {
		DateTimeFormatter lmFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
		
		for (FeedInfo fi : this.feedpaths.values()) {
			try {
				XElement sel = new XElement("url");
				
				// TODO except for Pages?  review
				
				sel.add(new XElement("loc", indexurl + fi.getUrlPath().substring(1)));
				sel.add(new XElement("lastmod", lmFmt.format(Files.getLastModifiedTime(fi.getPubpath()).toInstant())));

				for (String lname : altlocales)
					sel.add(new XElement("xhtml:link")
						.withAttribute("rel", "alternate")
						.withAttribute("hreflang", lname)
						.withAttribute("href", indexurl + lname + fi.getUrlPath())
					);
				
				smel.add(sel);
			}
			catch (Exception x) {
				Logger.error("Unable to add " + fi.getUrlPath() + ": " + x);
			}
		}
	}
}
