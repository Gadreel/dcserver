package dcraft.web.ui.inst;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.util.RndUtil;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class Footer extends Main {
	static public Footer tag() {
		Footer el = new Footer();
		el.setName("dc.Footer");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Footer.tag();
	}
	
	@Override
	public String getDefaultLabel() {
		return "Footer";
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this
				.withAttribute("data-dc-enhance", "true")
				.withAttribute("data-dc-tag", this.getName());
		
		this.setName("footer");
	}
}
