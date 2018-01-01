package dcraft.web.ui.inst.layout;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.script.inst.doc.Base;
import dcraft.xml.XElement;

public class Column extends Base {
	static public Column tag() {
		Column el = new Column();
		el.setName("dc.Column");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Column.tag();
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		String colsize = StackUtil.stringFromSource(state, "Size", "1-4");
		
		// None, Small, Medium, Large, Extra
		String pad = StackUtil.stringFromSource(state, "Pad", "none").toLowerCase();
		
		this.withClass("pure-u", "dc-layout-column", "dc-layout-pad-" + pad, "pure-u-" + colsize);
		
		this.setName("div");
	}
}
