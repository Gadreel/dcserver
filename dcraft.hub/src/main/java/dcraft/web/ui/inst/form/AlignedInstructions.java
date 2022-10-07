package dcraft.web.ui.inst.form;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.work.InstructionWork;
import dcraft.util.RndUtil;
import dcraft.web.ui.UIUtil;
import dcraft.script.inst.doc.Base;
import dcraft.web.ui.inst.W3;
import dcraft.xml.XElement;

public class AlignedInstructions extends Base {
	static public AlignedInstructions tag() {
		AlignedInstructions el = new AlignedInstructions();
		el.setName("dcf.AlignedInstructions");
		return el;
	}
	
	protected String fieldid = null;
	
	@Override
	public XElement newNode() {
		return AlignedInstructions.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		if (this.hasNotEmptyAttribute("id"))
			this.fieldid = this.getAttribute("id");
		else 
			this.fieldid = "gen" + RndUtil.nextUUId();
		
		XElement root = UIUtil.translate(state, this, true);
		
		Base inst = (Base) W3.tag("div")
				.withAttribute("class", "dc-message dc-message-info");
		
		if (root != null) {
			// root is just a container and has no value
			inst.replaceChildren(root);
		}
		else {
			// TODO add warning if guest, add symbol if CMS
		}
		
		this.clearChildren();		// remove them from here
		
		this.with(W3.tag("div").withClass("dc-spacer"));
		
		this.with(W3.tag("div")
			.withClass("dc-control")
			.with(inst)							// fill up like a control
		);
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this
			.withClass("dc-field")
			.withAttribute("id", "fld" + this.fieldid);
		
		Form frm = this.getForm(state);
		
		if (this.getAttributeAsBooleanOrFalse("Invalid") || ((frm != null) && frm.getAttributeAsBooleanOrFalse("Invalid")))
			this.withClass("dc-invalid");
		
		if (this.getAttributeAsBooleanOrFalse("Stacked") || ((frm != null) && frm.getAttributeAsBooleanOrFalse("Stacked")))
			this.withClass("dc-field-stacked");
		
		this.setName("div");
	}
}
