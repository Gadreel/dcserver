package dcraft.web.ui.inst.form;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.work.InstructionWork;
import dcraft.web.ui.inst.W3;
import dcraft.xml.XElement;

public class SelectField extends CoreField {
	static public SelectField tag() {
		SelectField el = new SelectField();
		el.setName("dcf.Select");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return SelectField.tag();
	}
	
	@Override
	public void addControl(InstructionWork state) throws OperatingContextException {
		InputControl input = InputControl.fromField(this, InputControl.tag());
		
		// copy the options over into the control
		input.replaceChildren(this.fieldinfo);

		this.with(W3.tag("div")
			.withClass("dc-control", "dc-input-group")
			.with(input)
		);
		
		/*
			<div class="dc-control dc-input-group">
				<select id="state">
					<option>AL</option>
					<option>CA</option>
					<option>IL</option>
				</select>		
			</div>
		*/		
	}
}
