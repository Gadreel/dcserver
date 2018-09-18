package dcraft.web.ui.inst.form;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.web.ui.inst.W3;
import dcraft.xml.XElement;

public class Range extends InputField {
	static public Range tag() {
		Range el = new Range();
		el.setName("dcf.Range");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Range.tag();
	}
	
	@Override
	public void addControl(InstructionWork state) throws OperatingContextException {
		if (! StackUtil.boolFromSource(state, "HideValue", false)) {
			this.fieldinfo.with(W3.tag("Info")
					.withAttribute("Label", StackUtil.stringFromSource(state, "value", "0"))
			);
		}
		
		super.addControl(state);
	}
}
