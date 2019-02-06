package dcraft.web.ui.inst.form;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.web.ui.inst.W3;
import dcraft.xml.XElement;

public class ButtonGroup extends CoreField {
	static public ButtonGroup tag() {
		ButtonGroup el = new ButtonGroup();
		el.setName("dcf.ButtonGroup");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return ButtonGroup.tag();
	}
	
	@Override
	public void addControl(InstructionWork state) throws OperatingContextException {
		/*
					
			<dcf.ButtonGroup ...			from CoreField
				Compact="true"
			>
				<Button Icon="square" Label="nnn" Click="aaa" />
				<Button Icon="circle" />
				<Button Icon="star" />
				<Button Icon="bell" />
			</dcf.ButtonGroup>
		 * 
		 
		 * 
		 */
		
		Base grp = W3.tag("div")
			.withClass("dc-control",
				this.getAttributeAsBooleanOrFalse("Compact") ?  "dc-button-group" : "dc-input-group");
		
		for (XElement el : this.fieldinfo.selectAll("*")) {
			String ename = el.getName();
			
			if ("Glyph".equals(ename) || "Info".equals(ename) || "Button".equals(ename)) {
				Base ic = InputControl.fromGylph(state, this, el);
				
				// not helpful in this context
				ic.withoutClass("dc-addon-glyph-button");
				
				grp.with(ic);
			}
		}

		this.with(grp);
	}
}
