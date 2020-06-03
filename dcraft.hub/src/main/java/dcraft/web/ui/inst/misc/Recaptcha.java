package dcraft.web.ui.inst.misc;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.struct.RecordStruct;
import dcraft.xml.XElement;

public class Recaptcha extends Base {
	static public Recaptcha tag() {
		Recaptcha el = new Recaptcha();
		el.setName("dc.Recaptcha");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Recaptcha.tag();
	}

	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		// TODO lookup recaptcha type - for now assuming Google
		String rtype = "Google";

		String alt = StackUtil.stringFromSource(state, "Alternate");

		XElement capsettings = ApplicationHub.getCatalogSettings("CAPTCHA", alt);

		if (capsettings == null)
			capsettings = ApplicationHub.getCatalogSettings("Google", alt);

		String key = null;
		String service = null;
		boolean disabled = false;

		if (capsettings != null) {
			service = capsettings.getAttribute("Service", "reCAPTCHA");		// google for now, until migrate away

			XElement rsettings = capsettings.find(service);

			if (rsettings != null) {
				key = rsettings.getAttribute("SiteKey");
			}

			disabled = rsettings.getAttributeAsBooleanOrFalse("Disabled");
		}

		// use either CheckEnabled or Func, combined won't likely work out well

		this
				.withClass("dc-recaptcha h-captcha")
				.attr("data-func", StackUtil.stringFromSource(state, "Func"))
				.attr("data-ready-func", StackUtil.stringFromSource(state, "ReadyFunc"))
				.attr("data-size", StackUtil.boolFromSource(state, "CheckEnabled") ? "visible" : "invisible")
				.attr("data-sitekey", disabled ? "" : key)
				.attr("data-service", service)
				.attr("data-type", rtype)
				.withAttribute("data-dc-enhance", "true")
				.withAttribute("data-dc-tag", this.getName());

		this.setName("div");
	}
}
