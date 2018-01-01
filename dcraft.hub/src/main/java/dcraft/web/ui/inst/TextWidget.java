package dcraft.web.ui.inst;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.script.inst.doc.Base;
import dcraft.web.ui.UIUtil;
import dcraft.web.ui.inst.cms.EditButton;
import dcraft.web.ui.inst.misc.SocialMediaIcon;
import dcraft.xml.XElement;

public class TextWidget extends Base {
	static public TextWidget tag() {
		TextWidget el = new TextWidget();
		el.setName("dc.TextWidget");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return TextWidget.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		// Safe|Unsafe
		String mode = StackUtil.stringFromSource(state,"Mode", "Unsafe");
		
		XElement root = UIUtil.translate(state, this, "safe".equals(mode.toLowerCase()));
		
		this.clearChildren();
		
		if (root != null) {
			// root is just a container and has no value
			this.replaceChildren(root);
		}
		else {
			// TODO add warning if guest, add symbol if CMS
		}
		
		// TODO check for parent with data-cms-feed
		if (this.hasNotEmptyAttribute("id") && OperationContext.getOrThrow().getUserContext().isTagged("Admin", "Editor")) {
			this
					.withAttribute("data-cms-editable", "true")
					.with(
							EditButton.tag().attr("title", "CMS - edit previous text area")
					);
		}
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this.withClass("dc-widget dc-widget-text")
			.withAttribute("data-dc-enhance", "true")
			.withAttribute("data-dc-tag", this.getName());
		
		this.setName("div");
    }
}
