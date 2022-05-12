package dcraft.web.ui.inst.form;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.web.ui.UIUtil;
import dcraft.web.ui.inst.Button;
import dcraft.web.ui.inst.W3;
import dcraft.xml.XElement;

public class TagSelector extends CoreField {
	static public TagSelector tag() {
		TagSelector el = new TagSelector();
		el.setName("dcf.TagSelector");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return TagSelector.tag();
	}
	
	@Override
	public void addControl(InstructionWork state) throws OperatingContextException {
		Base grp = W3.tag("div")
			.withClass("dc-control dc-tag-selector");

		grp
			.withAttribute("id", this.fieldid)
			.withAttribute("data-dc-enhance", "true")
			.withAttribute("data-dc-tag", this.getName());

		if (this.hasNotEmptyAttribute("Trees"))
			grp.attr("data-dc-tag-trees", StackUtil.stringFromSource(state,"Trees"));

		grp
			.with(W3.tag("div")
					.withClass("dc-tag-selector-list-area")
					.with(
						W3.tag("div")
							.withClass("dc-tag-selector-listing")
					)
			)
			.with(W3.tag("div")
					.withClass("dc-tag-selector-btn")
					.with(Button.tag()
							.attr("Label", "Select")
							.attr("Icon", "fas/pencil-alt")
					)
			);

		RadioControl.enhanceField(this, grp);

		this.with(grp);
	}

	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		UIUtil.requireStyle(this, state, "/css/includes/dcf.tag-selector.css");
		UIUtil.requireScript(this, state, "/js/includes/dcf.tag-selector.js");

		super.renderBeforeChildren(state);
	}
}
