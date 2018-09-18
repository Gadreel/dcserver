package dcraft.web.ui.inst.form;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.web.ui.inst.W3;
import dcraft.xml.XElement;

public class Text extends InputField {
	static public Text tag() {
		Text el = new Text();
		el.setName("dcf.Text");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Text.tag();
	}
}
