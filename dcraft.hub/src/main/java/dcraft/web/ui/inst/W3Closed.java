package dcraft.web.ui.inst;

import dcraft.script.inst.With;
import dcraft.xml.XElement;

public class W3Closed extends W3 {
	static public W3Closed tag(String name) {
		W3Closed el = new W3Closed();
		el.setName(name);
		return el;
	}
	
	@Override
	public XElement newNode() {
		return W3Closed.tag(this.tagName);
	}
}
