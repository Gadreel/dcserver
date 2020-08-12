package dcraft.web.ui.inst;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.util.StringUtil;
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
		List<XElement> icons = this.selectAll("Icon");

		this.clearChildren();

		for (XElement icon : icons) {
			String path = StackUtil.stringFromElement(state, icon, "Path");

			if (StringUtil.isEmpty(path)) {
				String library = StackUtil.stringFromElement(state, icon, "Library");
				String name = StackUtil.stringFromElement(state, icon, "Name");

				path = library + "/" + name;
			}

			if (StringUtil.isNotEmpty(path)) {
				if (path.startsWith("/"))
					path = path.substring(1);

				String id = path.replace('/', '-');

				String vb = UIUtil.requireIcon(this, state, path);

				this.with(Base.tag("svg")
						.attr("class", "dc-icon-stack svg-inline--fa fa5-w-12 " + icon.getAttribute("class", "")
								+ " icon-" + id)
						.attr("xmlns", "http://www.w3.org/2000/svg")
						.attr("aria-hidden", "true")
						.attr("role", "img")
						.attr("viewBox", vb)
						.with(W3.tag("use")
								.attr("href", "#" + id)
								.attr("xlink:href", "#" + id)
						)
				);
			}
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
