package dcraft.web.ui.inst;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.AnyStruct;
import dcraft.util.RndUtil;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.util.ArrayList;
import java.util.List;

public class MenuWidget extends Base {
	static public MenuWidget tag() {
		MenuWidget el = new MenuWidget();
		el.setName("dc.MenuWidget");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return MenuWidget.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		String mnuid = StackUtil.stringFromSource(state, "id");
		// None, Open, Close, Both
		String ctrls = StackUtil.stringFromSource(state, "Controls", "None").toLowerCase();
		
		// include a Param if menu was built separately
		Struct funcwrap = StackUtil.queryVariable(state, StackUtil.stringFromSource(state, "Include"));

		XElement pel = null;

		if (funcwrap instanceof XElement) {
			pel = (XElement) funcwrap;
		}
		else if (funcwrap instanceof AnyStruct) {
			pel = ((XElement) ((AnyStruct) funcwrap).getValue());
		}

		if (pel != null) {
			this.mergeDeep(pel, false);
		}

		List<XElement> links = this.selectAll("dc.Link");
		
		this.children = new ArrayList<>();

		if ((links.size() == 0) && this.hasNotEmptyAttribute("Source")) {
			XElement menu = Struct.objectToXml(StackUtil.refFromSource(state, "Source"));
			String[] options = StackUtil.stringFromSource(state, "Options", "").split(",");
			long depth = StackUtil.intFromSource(state, "Level", 1);

			LevelInfo menulevel = MenuWidget.findLevel(state, menu, (int) depth, 1);

			if (menulevel != null) {
				for (XElement x : menulevel.level.selectAll("*")) {
					String[] mnuoptions = StackUtil.stringFromElement(state, x,"Options", "").split(",");

					boolean opass = // ((options.length == 1) && StringUtil.isEmpty(options[0])) &&
							((mnuoptions.length == 1) && StringUtil.isEmpty(mnuoptions[0]));

					for (int o1 = 0; ! opass && (o1 < options.length); o1++) {
						for (int o2 = 0; ! opass && ( o2 < mnuoptions.length); o2++) {
							if (options[o1].equals(mnuoptions[o2]))
								opass = true;
						}
					}

					if (opass) {
						links.add(Link.tag()
								.attr("Label", x.getAttribute("Title"))
								.attr("Page",  x.hasNotEmptyAttribute("Page")
										? x.getAttribute("Page")
										: menulevel.slug + "/" + x.getAttribute("Slug")
								)
						);
					}
				}
			}
		}
		
		// TODO review/improve - in css too
		if ("both".equals(ctrls) || "open".equals(ctrls)) {
			Base ctrlul = W3.tag("ul")
					.withClass("dc-menu-control");
			
			// add open up mobile menu button
			ctrlul.with(
					W3.tag("li")
							.withClass("dc-menu-list-item", "dc-menu-display-narrow")
							.with(Link.tag()
									.withClass("dc-menu-open")
									.attr("aria-label", OperationContext.getOrThrow().tr("_code_60000"))
									.withAttribute("Icon", "fa-bars")
									.withAttribute("IconSize", "2x")
							)
			);
			
			this.with(ctrlul);
		}
		
		Base ul = W3.tag("ul")
				.withClass("dc-menu-list", "dc-menu-mobile-disable");

		RecordStruct req = OperationContext.getOrThrow().getController().getFieldAsRecord("Request");
		String ppath = req.getFieldAsString("Path");
		String opath = req.getFieldAsString("OriginalPath");

		for (XElement mnu : links) {
			if (mnu.hasAttribute("Badges")) {
				String[] tags = StackUtil.stringFromElement(state, mnu,"Badges").split(",");

				boolean auth = ((tags == null) || OperationContext.getOrThrow().getUserContext().isTagged(tags));

				if (! auth)
					continue;
			}

			// Both, Wide or Narrow
			String display = StackUtil.stringFromElement(state, mnu, "Display", "Both").toLowerCase();

			String pagelink = StackUtil.stringFromElement(state, mnu, "Page");

			if (StringUtil.isNotEmpty(mnuid) && mnu.hasEmptyAttribute("id") && StringUtil.isNotEmpty(pagelink)) {
				mnu.withAttribute("id", mnuid + "-" + pagelink.substring(1).replace('/', '-'));
			}

			if (StringUtil.isNotEmpty(pagelink)) {
				if ((ppath.length() > 1) && ppath.startsWith(pagelink)) {
					((Base) mnu).withClass("selected");
				}
				else if ((opath.length() > 1) && opath.startsWith(pagelink)) {
					((Base) mnu).withClass("selected");
				}
				else {
					String[] pagelinks = StackUtil.stringFromElement(state, mnu, "AltSelects", "").split(",");

					for (int i = 0; i < pagelinks.length; i++) {
						if (StringUtil.isNotEmpty(pagelinks[i])) {
							if ((ppath.length() > 1) && ppath.startsWith(pagelinks[i])) {
								((Base) mnu).withClass("selected");
							} else if ((opath.length() > 1) && opath.startsWith(pagelinks[i])) {
								((Base) mnu).withClass("selected");
							}
						}
					}
				}
			}

			ul.with(
					W3.tag("li")
						.withClass("dc-menu-list-item", "dc-menu-display-" + display)
						.with(mnu)
			);
		}
		
		if ("both".equals(ctrls) || "close".equals(ctrls)) {
			// add close up mobile menu button
			ul.with(
					W3.tag("li")
							.withClass("dc-menu-list-item", "dc-menu-display-narrow")
							.with(Link.tag()
									.withClass("dc-menu-close")
									.attr("aria-label", OperationContext.getOrThrow().tr("_code_60001"))
									.withAttribute("Icon", "fa-chevron-up")
									.withAttribute("IconSize", "2x")
							)
			);
		}
		
		this.with(ul);

		if (this.hasEmptyAttribute("aria-labelledby") && this.hasEmptyAttribute("aria-label")) {
			String label = StackUtil.stringFromSource(state, "Label", "{$_Tr.dcwPageNavigation}");

			if (StringUtil.isNotEmpty(label)) {
				boolean ariaOnly = StackUtil.boolFromSource(state, "AriaOnly", true);

				String id = StackUtil.stringFromSource(state,"id");

				if (StringUtil.isEmpty(id)) {
					id = "gen" + RndUtil.nextUUId();
					this.withAttribute("id", id);
				}

				this.add(0, W3.tag("h3")
						.withClass(ariaOnly ? "dc-element-hidden" : "")
						.attr("id", id + "Header")
						.attr("tabindex", "-1")
						.with(
								W3.tag("span").withText(label)
						)
				);
			}
		}
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this
				.withClass("dc-widget", "dc-widget-menu")
				.withAttribute("data-dc-enhance", "true")
				.withAttribute("data-dc-tag", this.getName());
		
		this.setName("nav");
    }

    static public LevelInfo findLevel(InstructionWork state, XElement parent, int depth, int current) throws OperatingContextException {
		LevelInfo result = null;

		if (parent == null)
			return result;

		if (depth == current) {
			result = new LevelInfo();
			result.level = parent;
		}
		else {
			RecordStruct page = (RecordStruct) StackUtil.queryVariable(state, "Page");

			ListStruct pathparts = page.getFieldAsList("OriginalPathParts");

			String part = pathparts.getItemAsString(current - 1);

			if (part == null)
				return result;

			for (XElement child : parent.selectAll("*")) {
				if (part.equals(child.getAttribute("Slug")))
					result = MenuWidget.findLevel(state, child, depth, current + 1);

				if (result != null)
					break;
			}
		}

		if ((result != null) && parent.hasNotEmptyAttribute("Slug"))
			result.slug = "/" + parent.getAttribute("Slug") + result.slug;

		return result;
	}

    static public class LevelInfo {
		public String slug = "";
		public XElement level = null;
	}
}
