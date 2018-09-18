package dcraft.web.ui.inst;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.util.RndUtil;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class Header extends Base {
	static public Header tag() {
		Header el = new Header();
		el.setName("dc.Header");
		return el;
	}

	public Header() {
		super();
	}

	@Override
	public XElement newNode() {
		return Header.tag();
	}

	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		this.withClass("dc-region");

		if (this.hasEmptyAttribute("aria-labelledby") && this.hasEmptyAttribute("aria-label")) {
			String label = StackUtil.stringFromSource(state, "Label", "{$_Tr.dcwPageHeader}");

			if (StringUtil.isNotEmpty(label)) {
				boolean ariaOnly = StackUtil.boolFromSource(state, "AriaOnly", true);

				String id = StackUtil.stringFromSource(state,"id");

				if (StringUtil.isEmpty(id)) {
					id = "gen" + RndUtil.nextUUId();
					this.withAttribute("id", id);
				}

				//this.attr("aria-labelledby", id + "Header");

				this.add(0, W3.tag("h2")
						.withClass("dc-region-header", ariaOnly ? "dc-element-hidden" : "")
						.attr("id", id + "Header")
						.attr("tabindex", "-1")
						.with(
								W3.tag("span").withText(label)
						)
				);
			}
		}
	}

	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		/*
		this
				.withAttribute("data-dc-enhance", "true")
				.withAttribute("data-dc-tag", this.getName())
				.withAttribute("tabindex", "-1");
				*/

		this.attr("role", "banner");

		this.setName("header");
	}
}
