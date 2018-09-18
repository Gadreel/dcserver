package dcraft.web.ui.inst.form;

import dcraft.xml.XElement;

public class Password extends InputField {
	static public Password tag() {
		Password el = new Password();
		el.setName("dcf.Password");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Password.tag();
	}
}
