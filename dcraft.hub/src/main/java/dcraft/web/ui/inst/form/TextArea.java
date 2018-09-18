package dcraft.web.ui.inst.form;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.work.InstructionWork;
import dcraft.web.ui.inst.W3;
import dcraft.xml.XElement;

public class TextArea extends CoreField {
	static public TextArea tag() {
		TextArea el = new TextArea();
		el.setName("dcf.TextArea");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return TextArea.tag();
	}
	
	@Override
	public void addControl(InstructionWork state) throws OperatingContextException {
		InputControl input = InputControl.fromField(this, InputControl.tag());

		this.with(W3.tag("div")
			.withClass("dc-control", "dc-input-group")
			.with(input)
		);
		
		/*
			<div class="dc-control dc-input-group">
				<textarea id="comment" name="comment" class="dc-textarea"></textarea>
			</div>
		*/		
	}
}
