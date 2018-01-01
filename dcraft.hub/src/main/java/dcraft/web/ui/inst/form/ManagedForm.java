package dcraft.web.ui.inst.form;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.work.InstructionWork;
import dcraft.xml.XElement;

public class ManagedForm extends Form {
	static public ManagedForm tag() {
		ManagedForm el = new ManagedForm();
		el.setName("dcf.ManagedForm");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return ManagedForm.tag();
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this.removeAttribute("RecordOrder");		// only Default record is allowed
		
		this.withAttribute("data-dcf-managed", "true");
		
		// defaults to true so that form prefills will still be saved
		if (! this.hasNotEmptyAttribute("AlwaysNew"))
			this.withAttribute("AlwaysNew", "true");
		
		super.renderAfterChildren(state);
	}
}
