package dcraft.mail.dcc.inst;

import dcraft.script.inst.doc.Base;
import dcraft.xml.XElement;
import dcraft.xml.XText;

public class InjectContent extends Base {
	static public InjectContent tag() {
		InjectContent el = new InjectContent();
		el.setName("dcc.InjectContent");
		return el;
	}

	@Override
	public XElement newNode() {
		return InjectContent.tag();
	}
}
