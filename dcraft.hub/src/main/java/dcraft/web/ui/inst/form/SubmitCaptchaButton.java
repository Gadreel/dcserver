package dcraft.web.ui.inst.form;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.work.InstructionWork;
import dcraft.web.ui.inst.Button;
import dcraft.web.ui.inst.CaptchaButton;
import dcraft.xml.XElement;

public class SubmitCaptchaButton extends CaptchaButton {
	static public SubmitCaptchaButton tag() {
		SubmitCaptchaButton el = new SubmitCaptchaButton();
		el.setName("dcf.SubmitCaptchaButton");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return SubmitCaptchaButton.tag();
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
