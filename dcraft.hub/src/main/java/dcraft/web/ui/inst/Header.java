package dcraft.web.ui.inst;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.util.RndUtil;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class Header extends Main {
	static public Header tag() {
		Header el = new Header();
		el.setName("dc.Header");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Header.tag();
	}
	
	@Override
	public String getDefaultLabel() {
		return "Header";
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this
				.withAttribute("data-dc-enhance", "true")
				.withAttribute("data-dc-tag", this.getName());
		
		this.setName("header");
	}
}
