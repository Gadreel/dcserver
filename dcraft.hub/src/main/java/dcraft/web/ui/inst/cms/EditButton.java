package dcraft.web.ui.inst.cms;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.work.InstructionWork;
import dcraft.web.ui.inst.Link;
import dcraft.xml.XElement;

public class EditButton extends Link {
	static public EditButton tag() {
		EditButton el = new EditButton();
		
		el.setName("dcmi.CmsLink");

		return el;
	}

	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		this
				.withClass("dcuiCmsButton", "dcuiCmsi")
				.withAttribute("IconType", "fa-square")
				.withAttribute("Icon", "fa-pencil");

		super.renderBeforeChildren(state);
	}

	@Override
	public XElement newNode() {
		return EditButton.tag();
	}
}
