package dcraft.web.ui.inst.form;

import java.util.ArrayList;
import java.util.List;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.work.InstructionWork;
import dcraft.struct.Struct;
import dcraft.web.ui.inst.W3;
import dcraft.xml.XElement;

public class InputField extends CoreField {
	static public InputField tag() {
		InputField el = new InputField();
		el.setName("dcf.Text");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return InputField.tag();
	}
	
	@Override
	public void addControl(InstructionWork state) throws OperatingContextException {
		if (Struct.objectToBooleanOrFalse(this.fieldinfo.getAttribute("ValidateButton")))
			this.fieldinfo.with(W3.tag("Button")
				.withAttribute("Icon", "fas/info-circle")
				.withAttribute("InvalidIcon", "fas/exclamation")
				.withAttribute("Flag", "true")
				.withAttribute("data-dc-enhance", "true")
				.withAttribute("data-dc-tag", "dcf.ValidateButton")
			);
		
		List<XElement> before = new ArrayList<>();
		XElement input = null;
		List<XElement> after = new ArrayList<>();
		
		List<XElement> curr = before;
		
		for (XElement el : this.fieldinfo.selectAll("*")) {
			String ename = el.getName();
			
			if ("Glyph".equals(ename) || "Info".equals(ename) || "Button".equals(ename)) {
				curr.add(InputControl.fromGylph(state, this, el));
			}
			else if ("Input".equals(ename)) {
				input = InputControl.fromField(this, el);
				curr = after;
			}
		}
		
		if (input == null) {
			input = InputControl.fromField(this, InputControl.tag());
			// if no input found then all glyphs become after
			after = before;
			before = null;
		}
		
		// TODO support ValidIcon - into after

		this.with(W3.tag("div")
			.withClass("dc-control", "dc-input-group")
			.withAll(before)
			.with(input)
			.withAll(after)
		);
		
		/*
		<div class="dc-control dc-input-group">
			<input id="name" type="text" placeholder="Username"  />
		</div>
		*/		
	}
}
