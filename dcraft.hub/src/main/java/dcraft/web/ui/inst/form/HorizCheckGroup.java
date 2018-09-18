package dcraft.web.ui.inst.form;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.web.ui.inst.W3;
import dcraft.xml.XElement;

public class HorizCheckGroup extends CoreField {
	static public HorizCheckGroup tag() {
		HorizCheckGroup el = new HorizCheckGroup();
		el.setName("dcf.HorizCheckGroup");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return HorizCheckGroup.tag();
	}
	
	@Override
	public void addControl(InstructionWork state) throws OperatingContextException {
		Base grp = W3.tag("div")
			.withClass("dc-control", "dc-controlgroup-horizontal");
		
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
