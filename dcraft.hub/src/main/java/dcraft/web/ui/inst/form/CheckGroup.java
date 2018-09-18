package dcraft.web.ui.inst.form;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.web.ui.inst.W3;
import dcraft.xml.XElement;

public class CheckGroup extends CoreField {
	static public CheckGroup tag() {
		CheckGroup el = new CheckGroup();
		el.setName("dcf.CheckGroup");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return CheckGroup.tag();
	}
	
	@Override
	public void addControl(InstructionWork state) throws OperatingContextException {
		Base grp = W3.tag("div")
			.withClass("dc-control", "dc-controlgroup-vertical");
		
		grp
			.withAttribute("id", this.fieldid)
			.withAttribute("data-dc-enhance", "true")
			.withAttribute("data-dc-tag", this.getName());
		
		CheckControl.enhanceField(this, grp);
		
		for (XElement el : this.fieldinfo.selectAll("Checkbox"))
			grp.with(CheckControl.fromCheckField(this, el));
		
		this.with(grp);
	}
}
