package dcraft.web.ui.inst.form;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.web.ui.inst.W3;
import dcraft.xml.XElement;

public class Checkbox extends CoreField {
	static public Checkbox tag() {
		Checkbox el = new Checkbox();
		el.setName("dcf.Checkbox");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Checkbox.tag();
	}
	
	@Override
	public void addControl(InstructionWork state) throws OperatingContextException {
		Base grp = W3.tag("div")
			.withClass("dc-control");
		
		grp
			.withAttribute("id", "ctrl" + this.fieldid)
			.withAttribute("data-dc-enhance", "true")
			.withAttribute("data-dc-tag", this.getName());
		
		CheckControl.enhanceField(this, grp);

		this.removeAttribute("Label");

		if (this.hasNotEmptyAttribute("LongLabel"))
			this.withAttribute("Label", this.getAttribute("LongLabel"));
		
		//this.withAttribute("Value", "true");	// always true if checked
		this.withAttribute("DataType", "Boolean");
		
		grp.with(CheckControl.fromCheckField(state,this, this));
		
		this.with(grp);
	}
}
