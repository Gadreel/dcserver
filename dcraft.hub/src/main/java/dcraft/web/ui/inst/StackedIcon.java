package dcraft.web.ui.inst;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.web.ui.UIUtil;
import dcraft.xml.XElement;

import java.util.ArrayList;
import java.util.List;

public class StackedIcon extends Base {
	static public StackedIcon tag() {
		StackedIcon el = new StackedIcon();
		el.setName("dc.StackedIcon");
		return el;
	}

	@Override
	public XElement newNode() {
		return StackedIcon.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		for (XElement icon : this.selectAll("Icon")) {
			String library = StackUtil.stringFromElement(state, icon, "Library");
			String name = StackUtil.stringFromElement(state, icon, "Name");

			String vb = UIUtil.requireIcon(this, state, library, name);

			icon
					.attr("class", "dc-icon-stack svg-inline--fa fa5-w-12 " + icon.getAttribute("class", "")
							+ " icon-" + library + "-" + icon)
					.attr("xmlns", "http://www.w3.org/2000/svg")
					.attr("aria-hidden", "true")
					.attr("role", "img")
					.attr("viewBox", vb)
					.with(W3.tag("use")
							.attr("href", "#" + library + "-" + name)
							.attr("xlink:href", "#" + library + "-" + name)
					)
					.setName("svg");
		}
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this
				.withClass("dc-icon-stacked fa5-stack")
				.attr("data-dc-tag", this.getName());
		
		if (! this.hasAttribute("aria-label") && ! this.hasAttribute("aria-labeled-by")) {
			this
					.attr("aria-hidden", "true")
					.attr("role", "img");
		}

		this.setName("span");
    }
}
