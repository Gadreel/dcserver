package dcraft.web.ui.inst.form;

import java.util.List;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.work.InstructionWork;
import dcraft.script.inst.doc.Base;
import dcraft.web.ui.inst.W3;
import dcraft.xml.XElement;

public class MultiInputField extends CoreField {
	static public MultiInputField tag() {
		MultiInputField el = new MultiInputField();
		el.setName("dcf.MultiText");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return MultiInputField.tag();
	}
	
	@Override
	public void addControl(InstructionWork state) throws OperatingContextException {
		Base grp = W3.tag("div")
			.withClass("dc-control", "dc-input-multi");
		
		/*
		List<XElement> inputs = this.fieldinfo.selectAll("Input");
		
		for (XElement el : inputs) 
			grp.with(InputControl.fromField(this, el));
		*/
		
		for (XElement el : this.fieldinfo.selectAll("*")) {
			String ename = el.getName();
			
			if ("Glyph".equals(ename) || "Info".equals(ename) || "Button".equals(ename)) {
				grp.with(InputControl.fromGylph(state, this, el));
			}
			else if ("Input".equals(ename)) {
				grp.with(InputControl.fromField(this, el));
			}
		}

		this.with(grp);
		
		/*
			<div class="dc-control dc-input-multi">
				<input id="first" placeholder="First" class="dc-input-40"  />
				<input id="mid" placeholder="M" class="dc-input-10"  />
				<input id="last" placeholder="Last"  class="dc-input-40" />
			</div>
		*/		
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		super.renderAfterChildren(state);

		this.withClass("dc-field-multi");
	}
}
