package dcraft.web.ui.inst;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.script.inst.doc.Base;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class Link extends Base {
	static public Link tag() {
		Link el = new Link();
		el.setName("dc.Link");
		return el;
	}
	
	static public Link tag(String name) {
		Link el = new Link();
		el.setName(name);
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Link.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		String label = StackUtil.stringFromSource(state,"Label");
		String icon = StackUtil.stringFromSource(state,"Icon");

		// square, circle, none/empty, simple
		String icontype = StackUtil.stringFromSource(state,"IconType", "fa-square").toLowerCase();
		String iconsize = StackUtil.stringFromSource(state,"IconSize", "lg");
		String to = StackUtil.stringFromSource(state,"To", "#");
		String click = StackUtil.stringFromSource(state,"Click");
		String page = StackUtil.stringFromSource(state,"Page");
		
		if (StringUtil.isNotEmpty(label)) {
			this.withText(label).withAttribute("title", label);
		}
		else if (StringUtil.isNotEmpty(icon)) {
			if ("none".equals(icontype) || "empty".equals(icontype)) {
				this.with(
						W3.tag("span")
								.withClass("fa-stack", "fa-" + iconsize)
								.withAttribute("aria-hidden","true")
								.with(
										W3.tag("i")
												.withClass("fa", icon, "fa-stack-1x", "dc-icon-foreground")
								)
				);
			}
			else if ("simple".equals(icontype)) {
				this.with(
						W3.tag("i")
								.withClass("fa", icon, "fa-" + iconsize, "dc-icon-foreground")
								.withAttribute("aria-hidden","true")
				);
			}
			else {
				this.with(
						W3.tag("span")
								.withClass("fa-stack", "fa-" + iconsize)
								.withAttribute("aria-hidden","true")
								.with(
										W3.tag("i").withClass("fa", icontype, "fa-stack-2x", "dc-icon-background"),
										W3.tag("i").withClass("fa", icon, "fa-stack-1x", "dc-icon-foreground")
								)
				);
			}
		}

		/*
			<span class="fa-stack fa-4x">
			  <i class="fa fa-circle fa-stack-2x icon-background4"></i>
			   <i class="fa fa-circle-thin fa-stack-2x icon-background6"></i>
			  <i class="fa fa-lock fa-stack-1x"></i>
			</span>
		 */


		if (StringUtil.isNotEmpty(page))
			this.withAttribute("data-dc-page", page);
		
		if (StringUtil.isNotEmpty(click))
			this.withAttribute("data-dc-click", click);
		
		this
				.withAttribute("href", StringUtil.isNotEmpty(page) ? page : to);
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this
				.withClass("dc-link")
				.withAttribute("data-dc-enhance", "true")
				.withAttribute("data-dc-tag", this.getName());
		
		this.setName("a");
    }
}
