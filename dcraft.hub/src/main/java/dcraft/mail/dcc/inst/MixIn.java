package dcraft.mail.dcc.inst;

import dcraft.script.inst.doc.Base;
import dcraft.xml.XElement;

public class MixIn extends Base {
	static public MixIn tag() {
		MixIn el = new MixIn();
		el.setName("dcc.MixIn");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return MixIn.tag();
	}

}
