package dcraft.web.ui.inst.form;

import dcraft.script.inst.doc.Base;
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
	public void addControl() {
		Base grp = W3.tag("div")
			.withClass("dc-control");
		
		grp
			.withAttribute("id", this.fieldid)
			.withAttribute("data-dc-enhance", "true")
			.withAttribute("data-dc-tag", this.getName());
		
		if ("dcf.HorizCheckGroup".equals(this.getName()))
			grp.withClass("dc-controlgroup-horizontal");
		else
			grp.withClass("dc-controlgroup-vertical");
		
		CheckControl.enhanceField(this, grp);
		
		if ("dcf.Checkbox".equals(this.getName())) {
			this.removeAttribute("Label");
			
			if (this.hasNotEmptyAttribute("LongLabel"))
				this.withAttribute("Label", this.getAttribute("LongLabel"));
			
			this.withAttribute("Value", "true");	// always true if checked
			this.withAttribute("DataType", "Boolean");
			
			grp.with(CheckControl.fromCheckField(this, this));
		}
		else {
			for (XElement el : this.fieldinfo.selectAll("Checkbox")) 
				grp.with(CheckControl.fromCheckField(this, el));
		}
		
		this.with(grp);
	}
}
