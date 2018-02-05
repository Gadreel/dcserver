package dcraft.web.ui.inst;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.util.RndUtil;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class Region extends Base {
	static public Region tag() {
		Region el = new Region();
		el.setName("dc.Region");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Region.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		this
				.withClass("dc-region");
				// TODO enable via "verbose" mode on client .attr("role", "region");
		
		if (this.hasEmptyAttribute("aria-labelledby") && this.hasEmptyAttribute("aria-label")) {
			String label = StackUtil.stringFromSource(state, "Label");
			boolean ariaOnly = false;
			
			if (StringUtil.isEmpty(label)) {
				label = StackUtil.stringFromSource(state, "Hint");
				ariaOnly = true;
			}
			
			if (StringUtil.isNotEmpty(label)) {
				String id = StackUtil.stringFromSource(state,"id");
				
				if (StringUtil.isEmpty(id)) {
					id = "gen" + RndUtil.nextUUId();
					this.withAttribute("id", id);
				}
				
				this.attr("aria-labelledby", id + "Header");
				
				this.add(0, W3.tag("h2")
						.withClass("dc-region-header", ariaOnly ? "dc-element-hidden" : "")
						.attr("id", id + "Header")
						.with(
								W3.tag("span").withText(label)
						)
				);
			}
		}
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this
				.withAttribute("data-dc-enhance", "true")
				.withAttribute("data-dc-tag", this.getName());
		
		this.setName("section");
	}
}
