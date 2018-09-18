package dcraft.web.ui.inst;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class Style extends Base {
	static public Style tag() {
		Style el = new Style();
		el.setName("dc.Style");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Style.tag();
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this.setName("style");
		
		if (! this.hasAttribute("type"))
			this.attr("type", "text/css");
		
		// TODO support nonce
		
		//System.out.println(this.getRoot(state).toPrettyString());
	}
}
