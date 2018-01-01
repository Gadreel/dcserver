package dcraft.web.ui.inst.form;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.util.RndUtil;
import dcraft.util.StringUtil;
import dcraft.web.ui.inst.W3;
import dcraft.xml.XElement;

public class AlignedField extends CoreField {
	static public AlignedField tag() {
		AlignedField el = new AlignedField();
		el.setName("dcf.Aligned");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return AlignedField.tag();
	}
	
	@Override
	public void addControl() {
		this.with(W3.tag("div")
			.withClass("dc-control")
			.withAll(this.fieldinfo.getChildren())
		);
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		super.renderBeforeChildren(state);
		
		if (this.hasEmptyAttribute("aria-labelledby") && this.hasEmptyAttribute("aria-label")) {
			String label = StackUtil.stringFromSource(state, "Hint");
			
			if (StringUtil.isNotEmpty(label)) {
				this
						.withClass("dc-region")
						.attr("role", "region");
				
				boolean ariaOnly = StackUtil.boolFromSource(state, "AriaOnly", true);
				
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
		if ("dcf.FormButtons".equals(this.getName()))
			this.withClass("dc-form-buttons", "dc-field-stacked");
		
		// TODO if FormButtons add a <noscript> explaining JS needs to be enabled
		
		super.renderAfterChildren(state);
	}
}
