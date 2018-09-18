package dcraft.web.ui.inst.layout;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.web.ui.UIUtil;
import dcraft.xml.XElement;

public class SimpleLayout extends Base {
	static public SimpleLayout tag() {
		SimpleLayout el = new SimpleLayout();
		el.setName("dc.SimpleLayout");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return SimpleLayout.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		UIUtil.markIfEditable(state, this);
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		// None, Small, Medium, Large, Extra
		String pad = StackUtil.stringFromSource(state, "Pad", "none").toLowerCase();
		
		this.withClass("dc-layout", "dc-layout-simple", "dc-simple-pad-" + pad);

		this.setName("div");
	}
}
