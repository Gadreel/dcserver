package dcraft.mail.dcc.inst;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.xml.XElement;

public class Fragment extends Base {
	static public Fragment tag() {
		Fragment el = new Fragment();
		el.setName("dcc.Fragment");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Fragment.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		this.withClass("dcc-fragment");
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) {
		// don't change my identity until after the scripts run
		this.setName("div");
	}
}
