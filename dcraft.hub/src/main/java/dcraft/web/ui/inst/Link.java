package dcraft.web.ui.inst;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.StackWork;
import dcraft.util.StringUtil;
import dcraft.web.ui.UIUtil;
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
		String icon = StackUtil.stringFromSource(state,"Icon");		// TODO old approach, remove after migration of all icon links
		String iconLibrary = StackUtil.stringFromSource(state,"IconLibrary");
		String iconName = StackUtil.stringFromSource(state,"IconName");
		
		// temp fix, eventually switch all to use lib/icon and clean up this code
		if (StringUtil.isNotEmpty(icon) && icon.contains("/")) {
			String[] iparts = icon.split("/");
			iconLibrary = iparts[0];
			iconName = iparts[1];
			icon = null;
		}

		// square, circle, none/empty, simple
		String icontype = StackUtil.stringFromSource(state,"IconType", "fa-square").toLowerCase();   // TODO set to `standard` as default after migration above
		String icontypeLibrary = StackUtil.stringFromSource(state,"IconTypeLibrary", "fas").toLowerCase();
		String icontypeName = StackUtil.stringFromSource(state,"IconTypeName", "square").toLowerCase();
		
		String iconsize = StackUtil.stringFromSource(state,"IconSize", "lg");
		String to = StackUtil.stringFromSource(state,"To", "#");
		String click = StackUtil.stringFromSource(state,"Click");
		String page = StackUtil.stringFromSource(state,"Page");
		
		if (StringUtil.isNotEmpty(label)) {
			this.withText(label).attr("dc-title", label);
		}
		else if (StringUtil.isNotEmpty(iconName)) {
			String vb = UIUtil.requireIcon(this, state, iconLibrary, iconName);
			
			XElement iconel = W3.tag("svg")
					.attr("xmlns", "http://www.w3.org/2000/svg")
					.attr("aria-hidden", "true")
					.attr("role", "img")
					.attr("viewBox", vb)
					.with(W3.tag("use")
							.attr("href", "#" + iconLibrary + "-" + iconName)
							.attr("xlink:href", "#" + iconLibrary + "-" + iconName)
					);
			
			if ("none".equals(icontype) || "empty".equals(icontype)) {
				iconel.attr("class", "dc-icon-stack svg-inline--fa fa5-w-12 fa5-stack-1x dc-icon-foreground icon-" + iconLibrary + "-" + iconName);
				
				this.with(
						W3.tag("span")
								.withClass("dc-icon-stacked", "fa5-stack", "fa5-" + iconsize)
								.attr("aria-hidden","true")
								.attr("role", "img")
								.with(
										iconel
								)
				);
			}
			else if ("simple".equals(icontype)) {
				iconel.attr("class", "dc-icon-foreground svg-inline--fa fa5-w-12 fa5-" + iconsize + " icon-" + iconLibrary + "-" + iconName);
				
				this.with(iconel);
			}
			else {
				iconel.attr("class", "dc-icon-stack svg-inline--fa fa5-w-12 fa5-stack-1x dc-icon-foreground fa5-inverse icon-" + iconLibrary + "-" + iconName);
				
				String vbb = UIUtil.requireIcon(this, state, icontypeLibrary, icontypeName);
				
				XElement iconelb = W3.tag("svg")
						.attr("class", "dc-icon-stack svg-inline--fa fa5-w-12 fa5-stack-2x dc-icon-background")
						.attr("xmlns", "http://www.w3.org/2000/svg")
						.attr("aria-hidden", "true")
						.attr("role", "img")
						.attr("viewBox", vbb)
						.with(W3.tag("use")
								.attr("href", "#" + icontypeLibrary + "-" + icontypeName)
								.attr("xlink:href", "#" + icontypeLibrary + "-" + icontypeName)
						);
				
				this.with(
						W3.tag("span")
								.withClass("dc-icon-stacked", "fa5-stack", "fa5-" + iconsize)
								.attr("aria-hidden","true")
								.attr("role", "img")
								.with(iconelb, iconel)
				);
			}
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
		
		if (StringUtil.isNotEmpty(to))
			this.withAttribute("data-dc-to", to);
		
		this
				.withAttribute("href", StringUtil.isNotEmpty(page) ? page : to);
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this
				.withClass("dc-link")
				.withAttribute("data-dc-enhance", "true")
				.withAttribute("data-dc-tag", this.getName());
		
		String badgelist = StackUtil.stringFromSource(state, "Badges");
		
		if (StringUtil.isNotEmpty(badgelist)) {
			String[] badges = badgelist.split(",");
			
			if (badges.length > 0) {
				this.setName(OperationContext.getOrThrow().getUserContext().isTagged(badges) ? "a" : "Ignore");
			}
		}
		else {
			this.setName("a");
		}
    }
}
