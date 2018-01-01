package dcraft.web.ui.inst;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.script.inst.doc.Base;
import dcraft.util.RndUtil;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;
import dcraft.xml.XNode;

import java.util.ArrayList;
import java.util.List;

public class PagePanel extends Base {
	static public PagePanel tag() {
		PagePanel el = new PagePanel();
		el.setName("dc.PagePanel");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return PagePanel.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		// the children will move into the body, so clear out our child list
		List<XNode> hiddenchildren = this.children;
		
		this.children = new ArrayList<>();

		String title = "{$Page.Title}";
		
		String id = StackUtil.stringFromSource(state,"id");
		
		if (StringUtil.isEmpty(id)) {
			id = "gen" + RndUtil.nextUUId();
			this.withAttribute("id", id);
		}
		
		// Default, Primary, Success, Info, Warning, Danger
		String scope = StackUtil.stringFromSource(state,"Scope", "Primary").toLowerCase();
		
		this
				.withClass("dc-panel", "dc-panel-" + scope, "dc-panel-page", "dc-focus");
				//.withAttribute("aria-labelledby", id + "Header")
				//.withAttribute("role", "region")
				//.withAttribute("tabindex", "-1");
		
		this.with(W3.tag("div")
				.withAttribute("class", "dc-panel-heading")
				.with(W3.tag("h1")
						.withAttribute("id", id + "Header")
						.withAttribute("aria-label", title + " page")
						.withText(title)
				)
				.with(Base.tag("nav")
					.attr("aria-label", "Secondary")
					.with(Base.tag("ul")
						.with(Base.tag("li")
								.with(Link.tag()
										.withClass("dc-panel-header-btn", "dcui-pagepanel-back")
										.withAttribute("title", "Previous Form")
										.withAttribute("Icon", "fa-chevron-left")
										.withAttribute("IconType", "Simple")
										.withAttribute("IconSize", "1x")
								)
						)
						.with(Base.tag("li")
								.with(Link.tag()
										.withClass("dc-panel-header-btn", "dcui-pagepanel-menu")
										.withAttribute("title", "Open Menu")
										.withAttribute("Icon", "fa-bars")
										.withAttribute("IconType", "Simple")
										.withAttribute("IconSize", "1x")
								)
						)
						/* TODO restore
						.with(Base.tag("li")
								.with(Link.tag()
										.withClass("dc-panel-header-btn", "dcui-pagepanel-help")
										.withAttribute("title", "Help")
										.withAttribute("Icon", "fa-question")
										.withAttribute("IconType", "Simple")
										.withAttribute("IconSize", "1x")
								)
						)
						*/
						.with(Base.tag("li")
								.with(Link.tag()
								.withClass("dc-panel-header-btn", "dcui-pagepanel-close")
									.withAttribute("title", "Close")
									.withAttribute("Icon", "fa-times")
									.withAttribute("IconType", "Simple")
									.withAttribute("IconSize", "1x")
								)
						)
					)
				)
			);
		
		XElement bodyui = W3.tag("main")
				.withAttribute("tabindex", "-1")
				.withAttribute("aria-labelledby", id + "Header")
				.withAttribute("class", "dc-panel-body")
				.withAttribute("id", id + "Body");

		if (hiddenchildren != null) {
			for (XNode n : hiddenchildren)
				bodyui.add(n);
		}
		
		this.with(bodyui);
    }
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this
				.withAttribute("data-dc-enhance", "true")
				.withAttribute("data-dc-tag", this.getName());
		
		this.setName("section");
	}
}
