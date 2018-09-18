package dcraft.web.ui.inst.form;

import dcraft.xml.XElement;

public class Hidden extends InputField {
	static public Hidden tag() {
		Hidden el = new Hidden();
		el.setName("dcf.Hidden");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Hidden.tag();
	}
}
