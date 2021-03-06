package dcraft.web.ui.inst.form;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.work.InstructionWork;
import dcraft.web.ui.inst.Button;
import dcraft.xml.XElement;

public class SubmitButton extends Button {
	static public SubmitButton tag() {
		SubmitButton el = new SubmitButton();
		el.setName("dcf.SubmitButton");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return SubmitButton.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		if (! this.hasNotEmptyAttribute("Label"))
			this.withAttribute("Label", "Submit");
		
		super.renderBeforeChildren(state);
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) {
		this.withClass("pure-button-primary");

		super.renderAfterChildren(state);
	}
}
