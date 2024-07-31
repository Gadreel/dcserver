package dcraft.mail.dcc.inst;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.util.StringUtil;
import dcraft.web.ui.inst.W3;
import dcraft.xml.XComment;
import dcraft.xml.XElement;
import dcraft.xml.XNode;
import dcraft.xml.XText;

import java.util.ArrayList;
import java.util.List;

public class EmailComment extends Base {
	static public EmailComment tag() {
		EmailComment el = new EmailComment();
		el.setName("dcc.EmailComment");
		return el;
	}
	
	static public EmailComment of(String comment) {
		EmailComment el = new EmailComment();
		el.setName("dcc.EmailComment");
		el.with(XText.ofRaw(comment));
		return el;
	}

	@Override
	public XElement newNode() {
		return EmailComment.tag();
	}
}
