package dcraft.web.ui.inst;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.script.inst.doc.Base;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;
import dcraft.xml.XNode;

import java.util.ArrayList;
import java.util.List;

public class Band extends Base {
	static public Band tag() {
		Band el = new Band();
		el.setName("dc.Band");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Band.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		// the children will move into the body, so clear out our child list
		List<XNode> hiddenchildren = this.children;
		
		this.children = new ArrayList<>();
		
		String id = StackUtil.stringFromSource(state,"id");
		
		// Full (aka None), Wide, Medium, Narrow
		String width = StackUtil.stringFromSource(state,"Width", "Wide").toLowerCase();
		
		this.withClass("dc-band");
		
		// None, Small, Medium, Large, Extra
		String pad = StackUtil.stringFromSource(state, "Pad", "none").toLowerCase();

		XElement bodyui = W3.tag("div")
				.withClass( "dc-band-wrapper", "dc-band-width-" + width, "dc-band-pad-" + pad);
		
		if (StringUtil.isNotEmpty(id))
			bodyui.withAttribute("id", id + "Body");
		
		if (hiddenchildren != null) {
			for (XNode n : hiddenchildren)
				bodyui.add(n);
		}
		
		this.with(bodyui);
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this
				.withAttribute("data-dc-enhance", "true")
				.withAttribute("data-dc-tag", this.getName());
		
		this.setName("div");
	}
}
