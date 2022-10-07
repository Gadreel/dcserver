package dcraft.web.ui.inst.form;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.work.InstructionWork;
import dcraft.web.ui.UIUtil;
import dcraft.script.inst.doc.Base;
import dcraft.xml.XElement;

public class Instructions extends Base {
	static public Instructions tag() {
		Instructions el = new Instructions();
		el.setName("dcf.Instructions");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Instructions.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		XElement root = UIUtil.translate(state, this, true);
		
		this.clearChildren();
		
		if (root != null) {
			// root is just a container and has no value
			this.replaceChildren(root);
		}
		else {
			// TODO add warning if guest, add symbol if CMS
		}
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this
			.withClass("dc-message", "dc-message-info");
		
		Form frm = this.getForm(state);
		
		if (this.getAttributeAsBooleanOrFalse("Invalid") || ((frm != null) && frm.getAttributeAsBooleanOrFalse("Invalid")))
			this.withClass("dc-invalid");
		
		if (this.getAttributeAsBooleanOrFalse("Stacked") || ((frm != null) && frm.getAttributeAsBooleanOrFalse("Stacked")))
			this.withClass("dc-field-stacked");
		
		this.setName("div");
	}
}
