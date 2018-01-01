package dcraft.web.ui.inst;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.script.StackUtil;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.struct.RecordStruct;
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
		
		List<XElement> links = this.selectAll("dc.Link");
		
		this.children = new ArrayList<>();
		
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
				if ((ppath.length() > 1) && pagelink.startsWith(ppath))
					((Base)mnu).withClass("selected");
				else if ((opath.length() > 1) && pagelink.startsWith(opath))
					((Base)mnu).withClass("selected");
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
									.withAttribute("Icon", "fa-chevron-up")
									.withAttribute("IconSize", "2x")
							)
			);
		}
		
		this.with(ul);
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this
				.withClass("dc-widget", "dc-widget-menu")
				.withAttribute("data-dc-enhance", "true")
				.withAttribute("data-dc-tag", this.getName());
		
		this.setName("nav");
    }
}
