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
		String path = StackUtil.stringFromSource(state, "Path");
		
		if (StringUtil.isEmpty(path)) {
			String library = StackUtil.stringFromSource(state,"Library");
			String icon = StackUtil.stringFromSource(state,"Name");
			
			path = library + "/" + icon;
		}
		
		this.clearChildren();
		
		if (StringUtil.isNotEmpty(path)) {
			if (path.startsWith("/"))
				path = path.substring(1);
			
			String id = path.replace('/', '-');
			
			String vb = UIUtil.requireIcon(this, state, path);
			
			this
					.withClass("icon-" + id)
					.attr("viewBox", vb)
					.with(W3.tag("use")
							.attr("href", "#" + id)
							.attr("xlink:href", "#" + id)
					);
		}
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
