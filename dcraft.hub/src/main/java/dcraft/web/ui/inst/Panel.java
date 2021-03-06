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

		if (this.children != null) {
			for (XNode node : this.children) {
				if ((node instanceof XElement) && (((XElement) node).getName().equals("Button"))) {
					btnchildren.add((XElement) node);
				} else {
					bodychildren.add(node);
				}
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
				
				if (ret.hasNotEmptyAttribute("Icon") || ret.hasNotEmptyAttribute("IconName"))
					ret.withClass("dc-panel-header-icon");

				if (ret.hasEmptyAttribute("IconType"))
					ret.withAttribute("IconType", "simple");
				
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
