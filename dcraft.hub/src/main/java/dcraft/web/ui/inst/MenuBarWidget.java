package dcraft.web.ui.inst;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
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

public class MenuBarWidget extends Base {
	static public MenuBarWidget tag() {
		MenuBarWidget el = new MenuBarWidget();
		el.setName("dc.MenuBarWidget");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return MenuBarWidget.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		// None, Open, Close, Both
		String ctrls = StackUtil.stringFromSource(state, "Controls", "None").toLowerCase();
		String[] options = StackUtil.stringFromSource(state, "Options", "").split(",");
		int maxdepth = (int) StackUtil.intFromSource(state, "Depth", 2);

		MenuWidget.LevelInfo topmenu = new MenuWidget.LevelInfo();

		if (this.hasNotEmptyAttribute("Source")) {
			XElement menu = Struct.objectToXml(StackUtil.refFromSource(state, "Source"));
			long level = StackUtil.intFromSource(state, "Level", 1);

			MenuWidget.LevelInfo menulevel = MenuWidget.findLevel(state, menu, (int) level, 1);

			if (menulevel != null) {
				topmenu = menulevel;
				topmenu.depth = 1;		// as far as the builder is concerned, we are at level 1
			}
		}
		else {
			topmenu.level = XElement.tag("Menu")
					.attr("Title", StackUtil.stringFromSource(state, "Title", "{$_Tr.dcwPageNavigation}"))
					.withAll(this.getChildren());

			topmenu.slug = StackUtil.stringFromSource(state, "Slug", "");
		}

		this.attr("aria-label", topmenu.level.attr("Title"));

		// clear children

		this.children = new ArrayList<>();

		// TODO require JS

		// first menu part

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
									.withAttribute("IconLibrary", "fas")
									.withAttribute("IconName", "bars")
							)
			);
			
			this.with(ctrlul);
		}

		// primary menu part
		Base ul = createMenu(state, options, topmenu, maxdepth)
				.withClass("dc-menu-mobile-disable");

		// final menu part
		if ("both".equals(ctrls) || "close".equals(ctrls)) {
			// add close up mobile menu button
			ul.with(
					W3.tag("li")
							.withClass("dc-menu-list-item", "dc-menu-display-narrow")
							.with(Link.tag()
									.withClass("dc-menu-close")
									.attr("aria-label", OperationContext.getOrThrow().tr("_code_60001"))
									.withAttribute("IconLibrary", "fas")
									.withAttribute("IconName", "chevron-up")
							)
			);
		}
		
		this.with(ul);
	}

	public Base createMenu(InstructionWork state, String[] options, MenuWidget.LevelInfo mnulevel, int maxdepth) throws OperatingContextException {
		Base ul = W3.tag("ul")
				.withClass("dc-menu-list");

		ul
				.attr("aria-label", mnulevel.level.attr("Title"));

		if (mnulevel.depth == 1) {
			ul.attr("role", "menubar");
		}
		else {
			ul.attr("role", "menu");
		}

		RecordStruct req = OperationContext.getOrThrow().getController().getFieldAsRecord("Request");
		String ppath = req.getFieldAsString("Path");
		String opath = req.getFieldAsString("OriginalPath");

		boolean firstAdded = false;

		for (XElement menu : mnulevel.level.selectAll("*")) {
			if (menu.hasAttribute("Badges")) {
				String[] tags = StackUtil.stringFromElement(state, menu,"Badges").split(",");

				boolean auth = ((tags == null) || OperationContext.getOrThrow().getUserContext().isTagged(tags));

				if (! auth)
					continue;
			}

			String[] mnuoptions = StackUtil.stringFromElement(state, menu,"Options", "").split(",");

			boolean opass = ((mnuoptions.length == 1) && StringUtil.isEmpty(mnuoptions[0]));

			for (int o1 = 0; ! opass && (o1 < options.length); o1++) {
				for (int o2 = 0; ! opass && ( o2 < mnuoptions.length); o2++) {
					if (options[o1].equals(mnuoptions[o2]))
						opass = true;
				}
			}

			if (! opass)
				continue;

			Link link = Link.tag();

			link
					.attr("Label", menu.getAttribute("Title"))
					.attr("role", "menuitem")
					.attr("tabindex", (! firstAdded && (mnulevel.depth == 1)) ? "0" : "-1");

			String slug = menu.hasNotEmptyAttribute("Slug")
					? mnulevel.slug + "/" + menu.getAttribute("Slug")
					: "";

			String pagelink = StackUtil.stringFromElement(state, menu, "Page");

			if (StringUtil.isEmpty(pagelink) && menu.hasNotEmptyAttribute("Slug"))
				pagelink = mnulevel.slug + "/" + menu.getAttribute("Slug");

			if (StringUtil.isNotEmpty(pagelink)) {
				if ((ppath.length() > 1) && ppath.startsWith(pagelink)) {
					link.withClass("selected");
				}
				else if ((opath.length() > 1) && opath.startsWith(pagelink)) {
					link.withClass("selected");
				}
				else {
					String[] pagelinks = StackUtil.stringFromElement(state, menu, "AltSelects", "").split(",");

					for (int i = 0; i < pagelinks.length; i++) {
						if (StringUtil.isNotEmpty(pagelinks[i])) {
							if ((ppath.length() > 1) && ppath.startsWith(pagelinks[i])) {
								link.withClass("selected");
							} else if ((opath.length() > 1) && opath.startsWith(pagelinks[i])) {
								link.withClass("selected");
							}
						}
					}
				}

				link.attr("Page", pagelink);
			}
			else if (menu.hasNotEmptyAttribute("To")) {
				link.attr("To", menu.attr("To"));
			}
			else if (! menu.isChildEmpty()) {
				link.attr("To", "#")
					.attr("aria-haspopup", "true")
					.attr("aria-expanded", "false");
			}

			String id = StackUtil.stringFromElement(state, menu, "id", "");
			String liclass = StackUtil.stringFromElement(state, menu, "class", "");

			XElement li = W3.tag("li")
					.withClass("dc-menu-list-item", liclass)
					.attr("id", id)
					.attr("role", "none")
					.with(link);

			if (! menu.isChildEmpty() && (mnulevel.depth < maxdepth)) {
				MenuWidget.LevelInfo sublvl = new MenuWidget.LevelInfo();
				sublvl.depth = mnulevel.depth + 1;
				sublvl.slug = slug;
				sublvl.level = menu;

				li.with(
						createMenu(state, options, sublvl, maxdepth)
				);
			}

			ul.with(li);

			firstAdded = true;
		}

		return ul;
	}

	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this
				.withClass("dc-widget", "dc-widget-menu")
				.attr("data-dc-enhance", "true")
				.attr("data-dc-tag", this.getName())
				.attr("itemtype", "https://schema.org/SiteNavigationElement");

		this.setName("nav");
    }

}
