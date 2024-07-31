package dcraft.mail.dcc.inst;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.util.StringUtil;
import dcraft.web.ui.inst.Html;
import dcraft.xml.XElement;

import java.util.Map;

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

	// if this block is a fragment that should be merged with root, call this during build
	@Override
	public void mergeWithRoot(InstructionWork state, Base root, boolean usemeta) throws OperatingContextException {
		// copy all attributes over, unless they have been overridden
		if (this.attributes != null) {
			for (Map.Entry<String, String> attr : this.attributes.entrySet())
				if (! root.hasAttribute(attr.getKey()) && ! "class".equals(attr.getKey()))
					root.setAttribute(attr.getKey(), attr.getValue());
		}

		boolean isSkel = StackUtil.boolFromElement(state, this, "Skeleton");

		// copy over the page class
		String pclass = StackUtil.stringFromElement(state, this,"class");

		if (StringUtil.isNotEmpty(pclass))
			root.withClass(pclass);

		// copy appropriate children over
		for (XElement el : this.selectAll("*")) {
			String tname = el.getName();

			if ("Style".equals(tname)) {
				// be sure that skeleton styles come before the others
				if (isSkel) {
					root.add(0, el);
				}
				// after the others, though this is not necessarily preferred, there isn't a better option
				else {
					root.add(el);
				}
			}
		}
	}
}
