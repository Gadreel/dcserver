/* ************************************************************************
#
#  designCraft.io
#
#  http://designcraft.io/
#
#  Copyright:
#    Copyright 2014 eTimeline, LLC. All rights reserved.
#
#  License:
#    See the license.txt file in the project's top-level directory for details.
#
#  Authors:
#    * Andy White
#
************************************************************************ */
package dcraft.web.ui.inst;

import dcraft.cms.util.FeedUtil;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.UserContext;
import dcraft.locale.LocaleUtil;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.inst.Instruction;
import dcraft.script.work.*;
import dcraft.script.inst.doc.Base;
import dcraft.struct.*;
import dcraft.struct.builder.JsonStreamBuilder;
import dcraft.struct.scalar.BooleanStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.task.IParentAwareWork;
import dcraft.tenant.Site;
import dcraft.util.Memory;
import dcraft.util.StringUtil;
import dcraft.util.io.OutputWrapper;
import dcraft.web.ui.JsonPrinter;
import dcraft.web.ui.UIUtil;
import dcraft.xml.XElement;
import dcraft.xml.XNode;
import dcraft.xml.XRawText;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Html extends Base {
	static public Html tag() {
		Html el = new Html();
		el.setName("dc.Html");
		return el;
	}

	static public void mergePageVariables(StackWork state, Base source) throws OperatingContextException {
		RecordStruct page = (RecordStruct) StackUtil.queryVariable(state, "Page");

		/*
		StackUtil.dumpVariableStack(state);

		System.out.println("##################################");

		RecordStruct vars = OperationContext.getOrThrow().getController();

		if (vars != null) {
			for (FieldStruct fld : vars.getFields()) {
				System.out.println("    - " + fld.getName() + " = " + fld.getValue());
			}
		}
		*/

		// feeds use site default
		String defloc = OperationContext.getOrThrow().getSite().getResources().getLocale().getDefaultLocale();

		String locale = OperationContext.getOrThrow().getLocale();

		for (XElement meta : source.selectAll("Meta")) {
			String name = meta.getAttribute("Name");

			// Name and Locale must be constants, thus don't resolve from stack
			if (StringUtil.isNotEmpty(name)) { // && ! page.hasField(name)) {
				page.with(name, StackUtil.resolveValueToString(state, FeedUtil.bestLocaleMatch(meta, locale, defloc)));
			}
		}
		
		ListStruct tags = page.getFieldAsList("Tags");
		
		if (tags == null) {
			tags = ListStruct.list();
			page.with("Tags", tags);
		}
		
		for (XElement meta : source.selectAll("Tag")) {
			String value = meta.getAttribute("Value");
			
			if (StringUtil.isNotEmpty(value))
				tags.with(value);
		}
	}

	protected List<XElement> icondefs = new ArrayList<>();

	protected Map<String, String> hiddenattributes = null;
	protected List<XNode> hiddenchildren = null;
	protected boolean headdone = false;

	public Map<String, String> getHiddenAttributes() {
		return this.hiddenattributes;
	}
	
	public List<XNode> getHiddenChildren() {
		return this.hiddenchildren;
	}
	
	@Override
	public XElement newNode() {
		return Html.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		Html.mergePageVariables(state, this);
		
		String skeleton = StackUtil.stringFromSource(state, "Skeleton");
		
		if (StringUtil.isNotEmpty(skeleton)) {
			this.with(IncludeFragmentInline.tag()
				.withAttribute("Path", "/skeletons/" + skeleton));
		}
	}
	
	@Override
	public void cleanup(InstructionWork state) throws OperatingContextException {
		if (this.headdone)
			return;
		
		super.cleanup(state);

		RecordStruct page = (RecordStruct) StackUtil.queryVariable(state, "Page");

		// fall back on Title attribute
		if (page.isFieldEmpty("Title")) {
			String title = StackUtil.stringFromSource(state, "Title");
			
			if (StringUtil.isNotEmpty(title))
				page.with("Title", title);
		}
		
		// make sure page variables are resolved (clean references)
		for (FieldStruct fld : page.getFields()) {
			Struct value = fld.getValue();
			
			if (value instanceof StringStruct) {
				StringStruct svalue = (StringStruct) value;
				
				svalue.setValue(StackUtil.resolveValueToString(state, svalue.getValueAsString(), true));
			}
		}
		
		
		// after cleanup the document will be turned in just body by Base
		// we only want head and body in translated document
		// set apart the rest for possible use later in dynamic out
		this.hiddenattributes = this.attributes;
		this.hiddenchildren = this.children;
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		// don't run twice
		if (this.headdone)
			return;

		Base body = (Base) this.find("body");
		
		if (body == null) {
			// TODO have a MissingWidget for these sorts of cases
			body = new Fragment();
			
			body
					.with(W3.tag("h1")
							.withText("Missing Body Error!!")
					);
			
			this.with(body);
		}
		
		String pc = StackUtil.stringFromSource(state,"PageClass");
		
		// TODO cleanup - not always base
		if (StringUtil.isNotEmpty(pc))
			((Base) this.findId("dcuiMain")).withClass(pc);
		
		for (XNode rel : this.hiddenchildren) {
			if (! (rel instanceof XElement))
				continue;
			
			XElement xel = (XElement) rel;
			
			if (xel.getName().equals("Require") && xel.hasNotEmptyAttribute("Class"))
				body.withClass(xel.getAttribute("Class"));

			if (xel.getName().equals("Require") && xel.hasNotEmptyAttribute("Icons")) {
				String[] icons = xel.attr("Icons").split(",");

				for (String icon : icons)
					UIUtil.requireIcon(this, state, icon);
			}
		}


		// insert the defs into the body
		if (this.icondefs.size() > 0) {
			body.add(0,
					W3.tag("svg")
						//.attr("id","dcIconDefs")
						.attr("xmlns", "http://www.w3.org/2000/svg")
						.attr("viewBox", "0 0 512 512")
						.attr("style", "display: none;")
						.with(W3.tag("defs")
								.withAll(this.icondefs)
						)
			);
		}

		RecordStruct page = (RecordStruct) StackUtil.queryVariable(state, "Page");
		
		// TODO add feature for page title pattern
		
		// TODO below stringFromSource won't work because of "hidden" concept - review and fix
		
		W3 head = W3.tag("head");
		
		head
				.with(W3.tag("meta")
						.withAttribute("chartset", "utf-8")
				)
				.with(W3.tag("meta")
						.withAttribute("name", "format-detection")
						.withAttribute("content", "telephone=no")
				)
				.with(W3.tag("meta")
						.withAttribute("name", "viewport")
						.withAttribute("content", "width=device-width, initial-scale=1")		//, maximum-scale=1.0, user-scalable=no")
				)
				.with(W3.tag("meta")
						.withAttribute("name", "robots")
						.withAttribute("content", ("false".equals(StackUtil.stringFromSource(state,"Public", "true").toLowerCase()))
								? "noindex,nofollow" : "index,follow")
				)
				.with(W3.tag("title").withText("{$Page.Title}"));
		
		Site site = OperationContext.getOrThrow().getSite();
		
		//XElement domainwebconfig = site.getWebConfig();
		
		XElement domainwebconfig = ResourceHub.getResources().getConfig().getTag("Web");
		
		String icon = StackUtil.stringFromSource(state,"Icon");
		
		if (StringUtil.isEmpty(icon))
			icon = StackUtil.stringFromSource(state,"Icon16");
		
		if (StringUtil.isEmpty(icon) && (domainwebconfig != null))
			icon = domainwebconfig.getAttribute("Icon");
		
		if (StringUtil.isEmpty(icon) && (domainwebconfig != null))
			icon = domainwebconfig.getAttribute("Icon16");
		
		if (StringUtil.isEmpty(icon))
			icon = "/imgs/logo";
		
		if (StringUtil.isNotEmpty(icon)) {
			// if full name then use as the 16x16 version
			if (icon.endsWith(".png")) {
				head
						.with(W3.tag("link")
								.withAttribute("type", "image/png")
								.withAttribute("rel", "shortcut icon")
								.withAttribute("href", icon)
						)
						.with(W3.tag("link")
								.withAttribute("sizes", "16x16")
								.withAttribute("rel", "icon")
								.withAttribute("href", icon)
						);
			}
			else {
				head
						.with(W3.tag("link")
								.withAttribute("type", "image/png")
								.withAttribute("rel", "shortcut icon")
								.withAttribute("href", icon + "16.png")
						)
						.with(W3.tag("link")
								.withAttribute("sizes", "16x16")
								.withAttribute("rel", "icon")
								.withAttribute("href", icon + "16.png")
						)
						.with(W3.tag("link")
								.withAttribute("sizes", "32x32")
								.withAttribute("rel", "icon")
								.withAttribute("href", icon + "32.png")
						)
						.with(W3.tag("link")
								.withAttribute("sizes", "152x152")
								.withAttribute("rel", "icon")
								.withAttribute("href", icon + "152.png")
						);

				if (domainwebconfig.getAttributeAsBooleanOrFalse("SolidIcons")) {
					head.with(W3.tag("link")
							.withAttribute("sizes", "180x180")
							.withAttribute("rel", "apple-touch-icon")
							.withAttribute("href", icon  + "180solid.png")
						)
						.with(W3.tag("link")
							.withAttribute("rel", "manifest")
							.withAttribute("href", "/dcw/manifest.json")
						);
				}
			}
		}
		
		icon = StackUtil.stringFromSource(state,"Icon32");
		
		if (StringUtil.isNotEmpty(icon)) {
			head.with(W3.tag("link")
					.withAttribute("sizes", "32x32")
					.withAttribute("rel", "icon")
					.withAttribute("href", icon)
			);
		}
		
		icon = StackUtil.stringFromSource(state,"Icon152");
		
		if (StringUtil.isNotEmpty(icon)) {
			head.with(W3.tag("link")
					.withAttribute("sizes", "152x152")
					.withAttribute("rel", "icon")
					.withAttribute("href", icon)
			);
		}

		
		/*
		 * 	Essential Meta Tags
		 *
			https://css-tricks.com/essential-meta-tags-social-media/
			
			- images:  Reconciling the guidelines for the image is simple: follow Facebook's
			recommendation of a minimum dimension of 1200x630 pixels (can go as low as 600 x 315)
			and an aspect ratio of 1.91:1, but adhere to Twitter's file size requirement of less than 1MB.
			
			- Title max 70 chars
			- Desc max 200 chars
		 */
		
		head
				.with(W3.tag("meta")
						.withAttribute("property", "og:title")
						.withAttribute("content", "{$Page.Title}")
				);
		
		if (page.isNotFieldEmpty("Keywords")) {
			head
				.with(W3.tag("meta")
						.withAttribute("name", "keywords")
						.withAttribute("content", "{$Page.Keywords}")
				);
		}
		
		if (page.isNotFieldEmpty("Description")) {
			head
				.with(W3.tag("meta")
						.withAttribute("name", "description")
						.withAttribute("content", "{$Page.Description}")
				)
				.with(W3.tag("meta")
						.withAttribute("property", "og:description")
						.withAttribute("content", "{$Page.Description}")
				);
		}
		
		String indexurl = null;
		
		if ((domainwebconfig != null) && domainwebconfig.hasNotEmptyAttribute("IndexUrl"))
			indexurl = domainwebconfig.getAttribute("IndexUrl");
		
		if (StringUtil.isNotEmpty(indexurl)) {
			StackUtil.addVariable(state, "IndexUrl", StringStruct.of(indexurl.substring(0, indexurl.length() - 1)));
			
			if (page.isFieldEmpty("Image"))
				page.with("Image", domainwebconfig.getAttribute("SiteImage"));
				
			head
					.with(W3.tag("meta")
							.withAttribute("property", "og:image")
							.withAttribute("content", "{$IndexUrl}{$Page.Image}")
					);
		}
		
		/* TODO review
			.with(W3.tag("meta")
				.withAttribute("name", "twitter:card")
				.withAttribute("content", "summary")
			);
		*/
		
		/* TODO review, generalize so we can override
		if (domainwebconfig != null) {
			for (XElement gel : domainwebconfig.selectAll("Meta")) {
				UIElement m = W3.tag("meta");
				
				for (Entry<String, String> mset : gel.getAttributes().entrySet())
					m.withAttribute(mset.getKey(), mset.getValue());
				
				head.with(m);
			}
		}
		*/
		
		// TODO research canonical url too
		
		boolean cachemode = site.isScriptStyleCached() && StringUtil.isEmpty(OperationContext.getOrThrow().getController().getFieldAsRecord("Request").getFieldAsString("View"));
		
		// --- styles ---
		
		List<XElement> styles = OperationContext.getOrThrow().getSite().webGlobalStyles(cachemode);
		
		for (XElement stag : styles)
			head.with(stag);
		
		// add in styles specific for this page so we don't have to wait to see them load
		// TODO enhance so style doesn't double load
		for (XNode rel : this.hiddenchildren) {
			if (! (rel instanceof XElement))
				continue;
			
			XElement xel = (XElement) rel;
			
			if (xel.getName().equals("Require") && xel.hasNotEmptyAttribute("Style"))
				head.with(W3.tag("link")
						.withAttribute("type", "text/css")
						.withAttribute("rel", "stylesheet")
						.withAttribute("href", xel.getAttribute("Style")));
		}
		
		// --- scripts ---
		
		List<XElement> scripts = OperationContext.getOrThrow().getSite().webGlobalScripts(cachemode);
		
		for (XElement stag : scripts)
			head.with(stag);
		
		/*
$(document).ready(function() {
	
	dc.pui.Loader.init();
});
		 */
		
		this
				.withAttribute("lang", OperationContext.getOrThrow().getLocaleDefinition().getLanguage())
				.withAttribute("dir", OperationContext.getOrThrow().getLocaleDefinition().isRightToLeft() ? "rtl" : "ltr");

		// put head at top
		this.add(0, head);

		// the inline script must be after above so we can pick out the body parts for the layout
		
		BooleanStruct isDynamic = (BooleanStruct) StackUtil.queryVariable(state, "_Controller.Request.IsDynamic");
		
		// first load, add in a script
		if ((isDynamic == null) || ! isDynamic.getValue()) {
			XElement googlesetting = ApplicationHub.getCatalogSettings("Google");
			XElement facebooksetting = ApplicationHub.getCatalogSettings("Facebook");
			
			Memory mem = new Memory();
			
			try (OutputWrapper out = new OutputWrapper(mem)) {
				try (PrintStream ps = new PrintStream(out)) {
					ps.append("function dcReadyScript() {\n");
					
					ps.append("if (! dc.handler)\n\tdc.handler = { };\n\n");
					
					ps.append("if (! dc.handler.settings)\n\tdc.handler.settings = { };\n\n");
					
					// TODO escape these
					if ((facebooksetting != null) && facebooksetting.hasNotEmptyAttribute("SignInAppId"))
						ps.append("dc.handler.settings.fbAppId = '" + facebooksetting.getAttribute("SignInAppId") + "';\n");
					
					if (googlesetting != null) {
						if (googlesetting.hasNotEmptyAttribute("TrackingCode"))
							ps.append("dc.handler.settings.ga = '" + googlesetting.getAttribute("TrackingCode") + "';\n");

						XElement captcha = googlesetting.find("reCAPTCHA");

						if ((captcha != null) && captcha.getAttributeAsBooleanOrFalse("Tracking") && !  captcha.getAttributeAsBooleanOrFalse("Disabled"))
							ps.append("dc.handler.settings.gcaptcha = '" + captcha.getAttribute("SiteKey") + "';\n");
					}

					ps.append("\n");
					
					JsonPrinter prt = new JsonPrinter();
					
					prt.setFormatted(true);
					prt.setOut(ps);
					
					JsonStreamBuilder jsb = prt.getStream();
					
					// user info
					try {
						ps.append("dc.user.setUserInfo(");
						jsb.startRecord();
						
						UserContext user = OperationContext.getOrThrow().getUserContext();
						
						for (FieldStruct fld : user.getFields()) {
							if (! "AuthToken".equals(fld.getName())) {
								jsb.field(fld.getName(), fld.getValue());
							}
						}
						
						jsb.endRecord();
						ps.append(");\n\n");
					}
					catch (Exception x) {
					}
					
					// page def
					prt.printPageDef(this, false);
					
					ps.append("\n\n");
					ps.append("\tdc.pui.Loader.init();\n");
					ps.append("};\n");
				}
			}
			catch (IOException x ) {
				Logger.error("Bad write inline script: " + x);
			}
			
			// TODO nounce inline script - CSP like this
			// script-src 'strict-dynamic' 'nonce-abcdefg'
			// https://www.html5rocks.com/en/tutorials/security/content-security-policy/
			
			head.with(W3.tag("script")
					.with(XRawText.of(mem.toString()))
			);
		}
		
		super.renderAfterChildren(state);
		
		this.setName("html");
	}
	
	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		ReturnOption ret = super.run(state);
		
		if (ret == ReturnOption.DONE) {
			if (this.headdone) {
				/* TODO move into a test runner
				System.out.println("----------------------------------- HTML ----------------------------------");
				//System.out.println(this.toPrettyString());

				XmlPrinter prt = new HtmlPrinter();

				prt.setFormatted(true);
				prt.setOut(System.out);

				prt.print(this);

				System.out.println();

				System.out.println("----------------------------------- JSON ----------------------------------");

				prt = new JsonPrinter();

				prt.setFormatted(true);
				prt.setOut(System.out);
				prt.print(this);

				System.out.println();

				System.out.println("----------------------------------- END ----------------------------------");
				*/

				return ret;
			}

			this.headdone = true;

			if (this.gotoHead(state))
				return ReturnOption.CONTINUE;
		}
		
		return ret;
	}

	public void addIconDef(XElement def) {
		this.icondefs.add(def);
	}

	public XElement getIconDef(String id) {
		for (int i = 0; i < this.icondefs.size(); i++) {
			XElement def = this.icondefs.get(i);

			if (id.equals(def.attr("id")))
				return def;
		}

		return null;
	}

	@Override
	public boolean gotoNext(InstructionWork state, boolean orTop) {
		// don't go further if the head is running as that was the last step
		if (this.headdone)
			return false;

		return super.gotoNext(state, orTop);
	}

	public boolean gotoHead(InstructionWork state) {
		BlockWork blockWork = (BlockWork) state;

		XElement head = this.find("head");

		if ((head == null) || ! (head instanceof Instruction))
			return false;

		blockWork.setCurrEntry(((Instruction) head).createStack(state));
		return true;
	}

	@Override
	public ReturnOption operation(StackWork stack, XElement code) throws OperatingContextException {
		if ("Require".equals(code.getName())) {
			XElement copy = code.deepCopy();

			UIUtil.cleanDocReferences(stack, copy);

			this.add(copy);

			return ReturnOption.CONTINUE;
		}

		return super.operation(stack, code);
	}

	@Override
	public InstructionWork createStack(IParentAwareWork state) {
		return MainWork.of(state, this);
	}
	
	public InstructionWork createStack() {
		return MainWork.of(null, this);
	}
}
