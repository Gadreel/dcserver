package dcraft.web.ui.inst.feed;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.work.InstructionWork;
import dcraft.util.StringUtil;
import dcraft.script.inst.doc.Base;
import dcraft.xml.XElement;

public class Instagram extends Base {
	static public Instagram tag() {
		Instagram el = new Instagram();
		el.setName("dcm.Instagram");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Instagram.tag();
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this
			.withAttribute("data-dc-enhance", "true")
			.withAttribute("data-dc-tag", this.getName());
		
		this.setName("div");
		
		this.withClass("dcm-ig-listing");
		
		String alternate = this.getAttribute("Alternate");
		
		if (StringUtil.isNotEmpty(alternate))
			this.withAttribute("data-dcm-instagram-alternate", alternate);
		
		String count = this.getAttribute("Count");
		
		if (StringUtil.isNotEmpty(count))
			this.withAttribute("data-dcm-instagram-count", count);
    }
}
