package dcraft.web.ui.inst;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.util.RndUtil;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class Main extends Base {
	static public Main tag() {
		Main el = new Main();
		el.setName("dc.Main");
		return el;
	}

	protected String headertag = "h1";

	@Override
	public XElement newNode() {
		return Main.tag();
	}
	
	public String getDefaultLabel() {
		return "Main";
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		this.withClass("dc-region");

		if (this.hasEmptyAttribute("aria-labelledby") && this.hasEmptyAttribute("aria-label")) {
			String label = StackUtil.stringFromSource(state, "Label", this.getDefaultLabel());
			
			if (StringUtil.isNotEmpty(label)) {
				boolean ariaOnly = StackUtil.boolFromSource(state, "AriaOnly", true);
				
				String id = StackUtil.stringFromSource(state,"id");
				
				if (StringUtil.isEmpty(id)) {
					id = "gen" + RndUtil.nextUUId();
					this.withAttribute("id", id);
				}
				
				this.attr("aria-labelledby", id + "Header");
				
				this.add(0, W3.tag(this.headertag)
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
		
		this.setName("main");
	}
}
