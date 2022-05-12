package dcraft.web.ui.inst.form;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.web.ui.UIUtil;
import dcraft.xml.XElement;

public class SingleFileSelector extends InputField {
	static public SingleFileSelector tag() {
		SingleFileSelector el = new SingleFileSelector();
		el.setName("dcf.SingleFileSelector");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return SingleFileSelector.tag();
	}

	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		if (this.hasNotEmptyAttribute("Source"))
			this.attr("data-dc-file-source", StackUtil.stringFromSource(state,"Source"));

		this.with(
			XElement.tag("Input"),
			XElement.tag("Button")
				.attr("Icon", "fas/ellipsis-h")
				.attr("class", "open")
		);

		super.renderBeforeChildren(state);
	}

	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		//UIUtil.requireStyle(this, state, "/css/includes/dcf.single-file-selector.css");
		UIUtil.requireScript(this, state, "/js/includes/dcf.single-file-selector.js");

		super.renderAfterChildren(state);
	}
}
