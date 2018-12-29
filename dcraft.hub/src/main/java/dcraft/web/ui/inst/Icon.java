package dcraft.web.ui.inst;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.script.StackUtil;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.util.StringUtil;
import dcraft.web.ui.UIUtil;
import dcraft.xml.XElement;

public class Icon extends Base {
	static public Icon tag() {
		Icon el = new Icon();
		el.setName("dc.Icon");
		return el;
	}

	@Override
	public XElement newNode() {
		return Icon.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		String library = StackUtil.stringFromSource(state,"Library");
		String icon = StackUtil.stringFromSource(state,"Name");

		this.clearChildren();

		String vb = UIUtil.requireIcon(this, state, library, icon);

		this
				.attr("viewBox", vb)
				.with(W3.tag("use")
						.attr("href", "#" + library + "-" + icon)
						.attr("xlink:href", "#" + library + "-" + icon)
				);
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this
				.withClass("dc-icon svg-inline--fa fa5-w-12")
				.attr("data-dc-tag", this.getName())
				.attr("xmlns", "http://www.w3.org/2000/svg");

		if (! this.hasAttribute("aria-label") && ! this.hasAttribute("aria-labeled-by")) {
			this
					.attr("aria-hidden", "true")
					.attr("role", "img");
		}

		this.setName("svg");
    }
}
