package dcraft.web.ui.inst.form;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.util.RndUtil;
import dcraft.util.StringUtil;
import dcraft.web.ui.inst.W3;
import dcraft.xml.XElement;

public class FormButtons extends Base {
	static public FormButtons tag() {
		FormButtons el = new FormButtons();
		el.setName("dcf.FormButtons");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return FormButtons.tag();
	}

	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		if (this.hasEmptyAttribute("aria-labelledby") && this.hasEmptyAttribute("aria-label")) {
			String label = StackUtil.stringFromSource(state, "Hint");
			
			if (StringUtil.isNotEmpty(label)) {
				this
						.withClass("dc-region");
						// TODO enable via "verbose" mode on client .attr("role", "region");
				
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
		this.withClass("dc-form-buttons");
		
		// TODO if FormButtons add a <noscript> explaining JS needs to be enabled

		this.setName("section");
	}
}
