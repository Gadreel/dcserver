package dcraft.web.ui.inst.form;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.web.ui.inst.W3;
import dcraft.xml.XElement;

public class Number extends InputField {
	static public Number tag() {
		Number el = new Number();
		el.setName("dcf.Number");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Number.tag();
	}
}
