package dcraft.web.ui.inst.form;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.work.InstructionWork;
import dcraft.struct.Struct;
import dcraft.web.ui.inst.W3;
import dcraft.xml.XElement;

import java.util.ArrayList;
import java.util.List;

public class Custom extends CoreField {
	static public Custom tag() {
		Custom el = new Custom();
		el.setName("dcf.Custom");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Custom.tag();
	}
	
	@Override
	public void addControl(InstructionWork state) throws OperatingContextException {
		this.fieldinfo
				.withClass("dc-control", "dc-input-group")
				.attr("id", this.fieldid + "Group")
				.setName("div");

		this.with(this.fieldinfo);
	}
}
