package dcraft.web.ui.inst.misc;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class SocialMediaWidget extends Base {
	static public ServerInfoWidget tag() {
		ServerInfoWidget el = new ServerInfoWidget();
		el.setName("dcm.SocialMediaWidget");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return ServerInfoWidget.tag();
	}
	
	public SocialMediaWidget() {
		super("dc.SocialMediaWidget");
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		String icons = StackUtil.stringFromSource(state, "For");
		String iconType = StackUtil.stringFromSource(state, "IconType");
		String iconSize = StackUtil.stringFromSource(state, "IconSize");
		
		if (StringUtil.isNotEmpty(icons)) {
			for (String icon : icons.split(",")) {
				this.with(SocialMediaIcon.tag()
						.withAttribute("For", icon)
						.withAttribute("IconType", iconType)
						.withAttribute("IconSize", iconSize)
				);
			}
		}
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) {
		this
				.withClass("dc-widget", "dcm-widget-social-media")
				.withAttribute("data-dc-enhance", "true")
				.withAttribute("data-dc-tag", this.getName());
		
		this.setName("div");
	}
}
