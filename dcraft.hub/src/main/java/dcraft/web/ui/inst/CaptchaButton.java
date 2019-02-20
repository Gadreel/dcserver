package dcraft.web.ui.inst;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class CaptchaButton extends Base {
	static public CaptchaButton tag() {
		CaptchaButton el = new CaptchaButton();
		el.setName("dc.CaptchaButton");
		return el;
	}
	
	static public CaptchaButton tag(String name) {
		CaptchaButton el = new CaptchaButton();
		el.setName(name);
		return el;
	}
	
	@Override
	public XElement newNode() {
		return CaptchaButton.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		String label = StackUtil.stringFromSource(state,"Label");
		String icon = StackUtil.stringFromSource(state,"Icon");
		String click = StackUtil.stringFromSource(state,"Click");
		String clickprep = StackUtil.stringFromSource(state,"ClickPrep");
		String action = StackUtil.stringFromSource(state,"Action", "default");

		this
				.attr("href", "#");
		
		if (StringUtil.isNotEmpty(label))
			this.withText(label).attr("dc-title", label);
		else if (StringUtil.isNotEmpty(icon))
			this.with(W3.tag("i").withAttribute("class", "fa " + icon)
				.withAttribute("aria-hidden","true")
			);


		// Default, Primary, Selected (TODO Success, Info, Warning, Danger)
		String scope = StackUtil.stringFromSource(state,"Scope", "Default").toLowerCase();
		
		this.withClass("pure-button-" + scope);

		if (StringUtil.isNotEmpty(click))
			this.withAttribute("data-dc-click", click);

		if (StringUtil.isNotEmpty(clickprep))
			this.withAttribute("data-dc-click-prep", clickprep);

		this.withAttribute("data-dc-action", action);

		String alt = StackUtil.stringFromSource(state, "Alternate");

		XElement gsettings = ApplicationHub.getCatalogSettings("Google", alt);

		if (gsettings != null) {
			XElement rsettings = gsettings.find("reCAPTCHA");

			if (rsettings != null) {
				String key = rsettings.getAttribute("SiteKey");

				this.withAttribute("data-dc-sitekey", key);
			}
		}
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) {
		this
				.withClass("pure-button", "dc-button")
				.withAttribute("role", "button")
				.withAttribute("tabindex", "0")
				.withAttribute("data-dc-enhance", "true")
				.withAttribute("data-dc-tag", this.getName());
		
		if (this.getName().startsWith("dc.Wide"))
			this
					.withClass("pure-button-wide");		// TODO wide
    
		this.setName("a");
    }
}
