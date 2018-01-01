package dcraft.web.ui.inst.layout;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.script.inst.doc.Base;
import dcraft.xml.XElement;

public class FullLayout extends Base {
	static public FullLayout tag() {
		FullLayout el = new FullLayout();
		el.setName("dc.FullLayout");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return FullLayout.tag();
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		// None, Small, Medium, Large, Extra
		String pad = StackUtil.stringFromSource(state, "Pad", "none").toLowerCase();
		
		this.withClass("pure-g dc-layout", "dc-layout-full", "dc-full-pad-" + pad);
		
		/*
		if (this.children != null) {
			for (XNode node : this.children) {
				if (node instanceof XElement) {
					XElement ui = (XElement) node;
					ui.setAttribute("class",  ui.getAttribute("class", "") + " pure-u pure-u-1");
				}
			}
		}
		*/
		
		this.setName("div");
	}
}
