package dcraft.web.ui.inst;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.script.inst.doc.Base;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;
import dcraft.xml.XNode;

import java.util.ArrayList;
import java.util.List;

public class Panel extends Base {
	static public Panel tag() {
		Panel el = new Panel();
		el.setName("dc.Panel");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Panel.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		// the children will move into the body, so clear out our child list
		List<XNode> bodychildren = new ArrayList<>();
		List<XElement> btnchildren = new ArrayList<>();
		
		for (XNode node : this.children) {
			if ((node instanceof XElement) && (((XElement)node).getName().equals("Button"))) {
				btnchildren.add((XElement) node);
			}
			else {
				bodychildren.add(node);
			}
		}
		
		this.children = new ArrayList<>();
		
		String title = StackUtil.stringFromSource(state,"Title");
		String id = StackUtil.stringFromSource(state,"id");
		
		// Default, Primary, Success, Info, Warning, Danger
		String scope = StackUtil.stringFromSource(state,"Scope", "Primary").toLowerCase();
		
		this.withClass("dc-panel", "dc-panel-" + scope);
		
		XElement heading = W3.tag("div")
				.withAttribute("class", "dc-panel-heading")
				.with(W3.tag("h2")
						.withClass("dc-control")
						.withText(title)
				);
		
		this.with(heading);
		
		if (btnchildren.size() > 0) {
			XElement nav = Base.tag("ul");
			
			for (XElement btn : btnchildren) {
				Base ret = Link.tag();
				
				ret.mergeDeep(btn, false);
				
				ret.withClass("dc-panel-header-btn");
				
				if (ret.hasNotEmptyAttribute("Icon"))
					ret.withClass("dc-panel-header-icon");

				if (ret.hasEmptyAttribute("IconType"))
					ret.withAttribute("IconType", "simple");
				
				/*
				String label = btn.getAttribute("Label");
				String icon = btn.getAttribute("Icon");
				
				String to = btn.getAttribute("To", "#");
				String click = btn.getAttribute("Click");
				String page = btn.getAttribute("Page");
				
				ret
						.attr("href", StringUtil.isNotEmpty(page) ? page : to)
						.attr("tabindex", "0")
						.attr("role", "button");
				
				if (btn.hasEmptyAttribute("data-dc-enhance"))
					ret
							.attr("data-dc-enhance", "true")
							.attr("data-dc-tag", "dc.Button");
				
				if (StringUtil.isNotEmpty(click))
					ret.attr("data-dc-click", click);
				else if (StringUtil.isNotEmpty(page))
					ret.attr("data-dc-page", page);
				else if (StringUtil.isNotEmpty(to))
					ret.attr("data-dc-to", to);
				
				
				if (StringUtil.isNotEmpty(icon))
					ret.with(W3.tag("i").withAttribute("class", "fa fa-fw " + icon));
				else if (StringUtil.isNotEmpty(label))
					ret.withText(label);
				
				if (btn.hasNotEmptyAttribute("aria-label"))
					ret.withAttribute("aria-label", btn.getAttribute("aria-label"));
				*/
				
				nav.with(
						W3.tag("li")
							.with(ret)
				);
			}
			
			heading.with(Base.tag("nav")
					.attr("aria-label", "Panel options")
					.with(nav)
			);
		}
		
		XElement bodyui = W3.tag("div")
				.withAttribute("class", "dc-panel-body");
		
		if (StringUtil.isNotEmpty(id))
			bodyui.withAttribute("id", id + "Body");

		if (bodychildren != null) {
			for (XNode n : bodychildren)
				bodyui.add(n);
		}
		
		this.with(bodyui);
    }
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this
				.withAttribute("data-dc-enhance", "true")
				.withAttribute("data-dc-tag", this.getName());
		
		this.setName("div");
	}
}
