package dcraft.web.ui;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dcraft.db.util.DocumentIndexBuilder;
import dcraft.filevault.work.FeedSearchWork;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.UserContext;
import dcraft.locale.LocaleUtil;
import dcraft.log.Logger;
import dcraft.script.Script;
import dcraft.script.StackUtil;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.struct.*;
import dcraft.struct.scalar.BooleanStruct;
import dcraft.task.*;
import dcraft.tenant.Site;
import dcraft.util.IOUtil;
import dcraft.util.StringUtil;
import dcraft.util.web.DateParser;
import dcraft.web.WebController;
import dcraft.web.md.MarkdownUtil;
import dcraft.web.ui.inst.*;
import dcraft.xml.XComment;
import dcraft.xml.XElement;
import dcraft.xml.XNode;
import dcraft.xml.XText;

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
			bestmatch = firstmatch;

		if (bestmatch == null)
			return null;
		
		String content = StackUtil.resolveValueToString(state, bestmatch.getValue());
		
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
	
	static public boolean isEditReady(InstructionWork state, XElement element) throws OperatingContextException {
		if (! UIUtil.canEdit(state, element))
			return false;

		String cmsmode = Struct.objectToString(StackUtil.queryVariable(null, "_Controller.Request.Cookies.dcmMode"));

		// TODO add support for dates as well - look into future for schedules when selected

		return ((cmsmode != null) && cmsmode.equals("now"));
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

	static public Task mockWebRequestTask(String tenant, String site, String title) throws OperatingContextException {
		WebController wctrl = WebController.forChannel(null, null);		// TODO someday load service settings if needed

		OperationContext wctx = OperationContext.context(UserContext.rootUser(tenant, site), wctrl);

		return Task.of(wctx)
				.withTitle(title);
	}

	static public Task mockWebRequestTask(Site site, String title) throws OperatingContextException {
		WebController wctrl = WebController.forChannel(null, null);		// TODO someday load service settings if needed

		OperationContext wctx = OperationContext.context(UserContext.rootUser(site), wctrl);

		return Task.of(wctx)
				.withTitle(title);
	}

	static public IWork dynamicToWork(TaskContext ctx, Path script) throws OperatingContextException {
		return UIUtil.dynamicToWork(ctx, Script.of(script));
	}

	static public IWork dynamicToWork(TaskContext ctx, Script script) throws OperatingContextException {
		WebController wctrl = (WebController) ctx.getController();

		RecordStruct req = wctrl.getFieldAsRecord("Request");

		String pathclass = req.getFieldAsString("Path").substring(1).replace('/', '-');

		if (pathclass.endsWith(".html"))
			pathclass = pathclass.substring(0, pathclass.length() - 5);
		else if (pathclass.endsWith(".dcs.xml"))
			pathclass = pathclass.substring(0, pathclass.length() - 8);

		pathclass = pathclass.replace('.', '_');

		// TODO cleanup everything about wctrl - including making this part more transparent
		RecordStruct page = RecordStruct.record()
				.with("Path", req.getFieldAsString("Path"))
				.with("PathParts", ListStruct.list((Object[]) req.getFieldAsString("Path").substring(1).split("/")))
				.with("OriginalPath", req.getFieldAsString("OriginalPath"))
				.with("OriginalPathParts", ListStruct.list((Object[]) req.getFieldAsString("OriginalPath").substring(1).split("/")))
				.with("PageClass", pathclass);

		wctrl.addVariable("Page", page);

		wctrl.addVariable("_Page", script.getXml());

		return script.toWork();
	}

	static public Script mdToDynamic(Path file) {
		CharSequence md = IOUtil.readEntireFile(file);

		if (md.length() == 0)
			return null;

		// TODO md = this.processIncludes(wctx, md);

		try {
			Html html = Html.tag();

			BufferedReader bufReader = new BufferedReader(new StringReader(md.toString()));

			String line = bufReader.readLine();

			RecordStruct fields = RecordStruct.record();

			// TODO enhance to become https://www.npmjs.com/package/front-matter compatible

			// start with $ for non-locale fields
			while (StringUtil.isNotEmpty(line)) {
				int pos = line.indexOf(':');

				if (pos == -1)
					break;

				String field = line.substring(0, pos);

				String value = line.substring(pos + 1).trim();

				fields.with(field, value);

				line = bufReader.readLine();
			}

			String locale = LocaleUtil.normalizeCode(fields.getFieldAsString("Locale", "eng"));  // should be a way to override, but be careful because 3rd party might depend on being "en", sorry something has to be default

			// TODO lookup alternative locales based on OC current locale

			for (FieldStruct fld : fields.getFields()) {
				String name = fld.getName();

				if (name.startsWith("$")) {
					html.with(
							W3.tag("Meta")
									.attr("Title", name.substring(1))
									.attr("Value", Struct.objectToString(fld.getValue()))
					);
				}
				else {
					html.with(
							W3.tag("Meta")
									.attr("Title", name.substring(1))
									.with(W3.tag("Tr")
											.attr("Locale", locale)
											.attr("Value", Struct.objectToString(fld.getValue()))
									)
					);
				}
			}

			// see if there is more - the body
			if (line != null) {
				XText mdata = new XText();
				mdata.setCData(true);

				line = bufReader.readLine();

				while (line != null) {
					mdata.appendBuffer(line);
					mdata.appendBuffer("\n");

					line = bufReader.readLine();
				}

				mdata.closeBuffer();

				html.with(IncludeParam.tag()
						.attr("Name", "Content")
						.with(
								TextWidget.tag()
										.with(W3.tag("Tr")
												.attr("Locale", locale)
												.with(mdata)
										)
						)
				);
			}

			String skeleton = fields.getFieldAsString("Skeleton", "general");
			
			if (! skeleton.startsWith("/"))
				skeleton = "/skeletons/" + skeleton;

			html.with(IncludeFragmentInline.tag()
					.withAttribute("Path", skeleton));

			return Script.of(html, md);
		}
		catch (Exception x) {
			System.out.println("md parse issue");
		}

		return null;
	}

	/*
		This is debatable but currently we use <header> only to mean page header area, nothing to do with content
		so for now everying in <header> (and <footer> and <hgroup>) is ignored by the indexing.
	 */
	static public void indexFinishedDocument(XElement current, DocumentIndexBuilder indexer) throws OperatingContextException {
		String tag = current.getName();

		int bonus = 0;
		boolean section = false;
		boolean add = false;

		if ("a".equals(tag) || "b".equals(tag) || "strong".equals(tag) || "i".equals(tag) || "em".equals(tag)) {
			bonus = 1;
			add = true;
		}
		else if ("body".equals(tag) || "div".equals(tag) || "li".equals(tag) || "main".equals(tag) || "ol".equals(tag) || "ul".equals(tag) || "section".equals(tag) || "span".equals(tag)) {
			add = true;
		}
		else if ("p".equals(tag)) {
			add = true;
			section = true;
		}
		else if ("h6".equals(tag)) {
			add = true;
			section = true;
			bonus = 1;
		}
		else if ("h5".equals(tag)) {
			add = true;
			section = true;
			bonus = 2;
		}
		else if ("h4".equals(tag)) {
			add = true;
			section = true;
			bonus = 3;
		}
		else if ("h3".equals(tag)) {
			add = true;
			section = true;
			bonus = 5;
		}
		else if ("h2".equals(tag)) {
			add = true;
			section = true;
			bonus = 8;
		}
		else if ("h1".equals(tag)) {
			add = true;
			section = true;
			bonus = 10;
		}

		if (add) {
			indexer.adjustScore(bonus);

			if (section)
				indexer.startSection();

			for (XNode node : current.getChildren()) {
				if (node instanceof XText) {
					String text = ((XText) node).getValue().trim() + " ";

					text = StackUtil.resolveValueToString(null, text,true);

					indexer.withText(text);
				}
				else if (node instanceof XElement) {
					UIUtil.indexFinishedDocument((XElement) node, indexer);
				}
			}

			if (section)
				indexer.endSection();

			// must come after end section
			indexer.adjustScore(-bonus);
		}
	}
	
	static public String iconConvertOld(String old) {
		if (StringUtil.isEmpty(old) || ! old.startsWith("fa-"))
			return old;
		
		if ("fa-ellipsis-h".equals(old))
			return "fas/ellipsis-h";
		
		if ("fa-upload".equals(old))
			return "fas/upload";
		
		if ("fa-image".equals(old))
			return "fas/image";
		
		if ("fa-plus".equals(old))
			return "fas/plus";
		
		if ("fa-minus".equals(old))
			return "fas/minus";
		
		if ("fa-question".equals(old))
			return "fas/question";
		
		if ("fa-search".equals(old))
			return "fas/search";
		
		if ("fa-calendar".equals(old))
			return "far/calendar-alt";
		
		return "fas/circle";
	}
	
	static public XElement requireSvgIcon(Base element, InstructionWork state, String path, String size) throws OperatingContextException{
		String vb = requireIcon(element, state, path);

		String id = path.replace('/', '-');
		
		return W3.tag("svg")
				.withClass("icon-" + id, size, "svg-inline--fa fa5-w-12")
				.attr("xmlns", "http://www.w3.org/2000/svg")
				.attr("aria-hidden", "true")
				.attr("role", "img")
				.attr("viewBox", vb)
				.with(W3.tag("use")
						.attr("href", "#" + id)
						.attr("xlink:href", "#" + id)
				);
	}
	
	static public XElement requireSvgIcon(Base element, InstructionWork state, String library, String name, String size) throws OperatingContextException{
		return requireSvgIcon(element, state, library + "/" + name, size);
	}
	
	static public String requireIcon(Base element, InstructionWork state, String library, String name) throws OperatingContextException {
		return requireIcon(element, state, library + "/" + name);
	}

	static public String requireIcon(Base element, InstructionWork state, String path) throws OperatingContextException {
		String vb = "0 0 512 512";
		String id = path.replace('/', '-');

		Base root = element.getRoot(state);

		if (! (root instanceof Html)) {
			Logger.warn("Require icon def failed: " + path);
			return vb;
		}

		// more efficient, not looking up same icon multiple times
		XElement icon = ((Html) root).getIconDef(id);

		if (icon != null) {
			vb = icon.attr("data-view-box");
			return vb;
		}

		XElement res = OperationContext.getOrThrow().getSite().getXmlResource("www", "/icons/" + path + ".svg", null);

		if (res != null) {
			vb = res.attr("viewBox");

			icon = W3.tag("g")
					.attr("id", id)
					.attr("data-view-box", vb);

			for (int n = 0; n < res.getChildCount(); n++) {
				if (res.getChild(n) instanceof XElement) {
					XElement pathel = res.getChildAsElement(n);

					if (! pathel.hasNotEmptyAttribute("fill"))
						pathel.attr("fill", "currentColor");

					icon.with(pathel);
				}
			}

			((Html) root).addIconDef(icon);
		}
		else {
			Logger.warn("Require icon missing: " + path);
		}

		return vb;
	}

	static public void cleanDocReferences(IParentAwareWork stack, XElement src) throws OperatingContextException {
		if (src.hasAttributes()) {
			for (String key : src.getAttributes().keySet()) {
				String value = src.getAttribute(key);
				value = StackUtil.resolveValueToString(stack, value, true);
				src.setAttribute(key, value);
			}
		}

		if (src.hasChildren()) {
			for (XNode node : src.getChildren()) {
				if (node instanceof XText) {
					String value = ((XText) node).getValue();
					value = StackUtil.resolveValueToString(stack, value, true);
					((XText) node).setValue(value);
				}
				else if (node instanceof XComment) {
					String value = ((XComment) node).getValue();
					value = StackUtil.resolveValueToString(stack, value, true);
					((XComment) node).setValue(value);
				}
				else if (node instanceof XElement) {
					UIUtil.cleanDocReferences(stack, (XElement) node);
				}
			}
		}
	}
}
