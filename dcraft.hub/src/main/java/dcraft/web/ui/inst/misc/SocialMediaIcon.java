package dcraft.web.ui.inst.misc;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.script.work.InstructionWork;
import dcraft.util.StringUtil;
import dcraft.web.ui.inst.Button;
import dcraft.web.ui.inst.Link;
import dcraft.xml.XElement;

public class SocialMediaIcon extends Link {
	static public SocialMediaIcon tag() {
		SocialMediaIcon el = new SocialMediaIcon();
		el.setName("dcm.SocialMediaIcon");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return SocialMediaIcon.tag();
	}

	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		String formedia = this.getAttribute("For");

		if (StringUtil.isNotEmpty(formedia)) {
			XElement setting = ApplicationHub.getCatalogSettings("Social-" + formedia, this.getAttribute("Alternate"));

			if ((setting != null) && ! this.hasAttribute("To"))
				this.withAttribute("To", setting.getAttribute("Url"));

			if (! this.hasAttribute("Icon")) {
				String iconname = formedia.toLowerCase();
				
				if ("facebook".equals(iconname))
					iconname = "facebook-f";
				else if ("linkedin".equals(iconname))
					iconname = "linkedin-in";
				
				this.withAttribute("IconName", iconname);
				this.withAttribute("IconLibrary", "fab");
			}
			
			if (! this.hasAttribute("aria-label"))
				this.withAttribute("aria-label", "{$_Tr.dcwTagSocialIcon} " + formedia);		// TODO locale, get from settings
		}

		super.renderBeforeChildren(state);
	}

	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this.withClass("dcm-social-media-icon");
		
		this.setName("dc.Link");
		
		super.renderAfterChildren(state);
	}
}
