package dcraft.web.ui.inst;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.work.InstructionWork;
import dcraft.script.inst.doc.Base;
import dcraft.xml.XElement;

public class Fragment extends Base {
	static public Fragment tag() {
		Fragment el = new Fragment();
		el.setName("dc.Fragment");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Fragment.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		this.withClass("dc-fragment");
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) {
		this
				.withAttribute("data-dc-enhance", "true")
				.withAttribute("data-dc-tag", this.getName());
		
		// don't change my identity until after the scripts run
		this.setName("div");
	}
}
