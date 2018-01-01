package dcraft.web.ui.inst;

import dcraft.script.inst.doc.Base;
import dcraft.xml.XElement;

public class W3 extends Base {
	static public W3 tag(String name) {
		W3 el = new W3();
		el.setName(name);
		return el;
	}
	
	@Override
	public XElement newNode() {
		return W3.tag(this.tagName);
	}
}
