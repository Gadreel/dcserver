package dcraft.web.ui.inst.form;

import java.util.Map.Entry;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.work.InstructionWork;
import dcraft.util.RndUtil;
import dcraft.util.StringUtil;
import dcraft.script.inst.doc.Base;
import dcraft.web.ui.UIUtil;
import dcraft.web.ui.inst.W3;
import dcraft.xml.XElement;

public class InputControl extends Base {
	static public Base fromGylph(InstructionWork state, CoreField fld, XElement input) throws OperatingContextException {
		W3 ret = W3.tag("span");

		if (input.hasNotEmptyAttribute("class"))
			ret.withClass(input.attr("class"));

		String gtype = input.getName();
		
		ret.withClass("dc-input-group-addon");

		String label = input.getAttribute("Label");
		String icon = UIUtil.iconConvertOld(input.getAttribute("Icon"));
		String invicon = UIUtil.iconConvertOld(input.getAttribute("InvalidIcon"));
		
		if ("Glyph".equals(gtype)) {
			ret.withClass("dc-addon-glyph");
		}
		else if ("Button".equals(gtype)) {
			ret.withClass("dc-addon-button");
			
			if (StringUtil.isNotEmpty(icon))
				ret.withClass("dc-addon-glyph-button");

			String to = input.getAttribute("To", "#");
			String click = input.getAttribute("Click");
			String page = input.getAttribute("Page");

			ret
					.attr("href", StringUtil.isNotEmpty(page) ? page : to)
					.attr("tabindex", "0")
					.attr("role", "button");

			if (input.hasEmptyAttribute("data-dc-enhance"))
				ret
						.attr("data-dc-enhance", "true")
						.attr("data-dc-tag", "dc.Button");

			if (StringUtil.isNotEmpty(click))
				ret.attr("data-dc-click", click);
			else if (StringUtil.isNotEmpty(page))
				ret.attr("data-dc-page", page);
			else if (StringUtil.isNotEmpty(to))
				ret.attr("data-dc-to", to);
		}
		else if ("Info".equals(gtype)) {
			ret.withClass("dc-addon-info");
		}
		
		if (input.getAttributeAsBooleanOrFalse("Flag"))
			ret.withClass("dc-valid-flag");
		
		if (StringUtil.isNotEmpty(icon) && StringUtil.isNotEmpty(invicon)) {
			ret
					.with(UIUtil.requireSvgIcon(fld, state, icon, "fa5-1x fa5-fw dc-invalid-hidden"))
					.with(UIUtil.requireSvgIcon(fld, state, invicon, "fa5-1x fa5-fw dc-valid-hidden dc-valid-flag"));
		}
		else if (StringUtil.isNotEmpty(icon)) {
			ret.with(UIUtil.requireSvgIcon(fld, state, icon, "fa5-1x fa5-fw"));
		}
		else if (StringUtil.isNotEmpty(label))
			ret.withText(label);

		if (input.hasNotEmptyAttribute("aria-label"))
			ret
					.withAttribute("aria-label", input.getAttribute("aria-label"));

		return ret;
	}
	
	static public InputControl fromField(CoreField fld, XElement input) {
		InputControl ic = (input instanceof InputControl) ? (InputControl) input : InputControl.tag();
		
		//Form frm = fld.getForm();
		ic.setName(fld.getName());

		if ("dcf.Password".equals(fld.getName())) {
			ic.withAttribute("type", "password");
		}
		/*
		else if ("dcf.Label".equals(fld.getName())) {
			// TODO enhance so this works with glyphs
		}
		else if ("dcf.Select".equals(fld.getName())) {
			// TODO enhance so this works with glyphs
		}
		else if ("dcf.TextArea".equals(fld.getName())) {
			ic.setName("dcf.TextArea");					
		}
		*/
		else if ("dcf.Hidden".equals(fld.getName())) {
			ic.withAttribute("type", "hidden");		// is otherwise just like a text field
		}
		else if ("dcf.Range".equals(fld.getName())) {
			ic.withAttribute("type", "range");		// is otherwise just like a text field

			if (! input.hasNotEmptyAttribute("min") && fld.hasNotEmptyAttribute("min"))
				ic.withAttribute("min", fld.getAttribute("min"));

			if (! input.hasNotEmptyAttribute("max") && fld.hasNotEmptyAttribute("max"))
				ic.withAttribute("max", fld.getAttribute("max"));

			if (! input.hasNotEmptyAttribute("step") && fld.hasNotEmptyAttribute("step"))
				ic.withAttribute("step", fld.getAttribute("step"));

			if (! input.hasNotEmptyAttribute("list") && fld.hasNotEmptyAttribute("list"))
				ic.withAttribute("list", fld.getAttribute("list"));
		}
		else if ("dcf.Number".equals(fld.getName())) {
			ic.withAttribute("type", "number");		// is otherwise just like a text field
			
			if (! input.hasNotEmptyAttribute("min") && fld.hasNotEmptyAttribute("min"))
				ic.withAttribute("min", fld.getAttribute("min"));
			
			if (! input.hasNotEmptyAttribute("max") && fld.hasNotEmptyAttribute("max"))
				ic.withAttribute("max", fld.getAttribute("max"));
		}
		else if (! input.hasNotEmptyAttribute("type")) {
			ic.withAttribute("type", "text");
		}
		
		if ("dcf.MultiText".equals(fld.getName())) {
			if (! input.hasNotEmptyAttribute("id"))
				ic.withAttribute("id", "gen" + RndUtil.nextUUId());
		}
		else if (! input.hasNotEmptyAttribute("id")) {
			ic.withAttribute("id", fld.fieldid);
		}
		
		if (! input.hasNotEmptyAttribute("readonly") && fld.hasNotEmptyAttribute("readonly"))
			ic.withAttribute("readonly", fld.getAttribute("readonly"));
		
		if (! input.hasNotEmptyAttribute("disabled") && fld.hasNotEmptyAttribute("disabled"))
			ic.withAttribute("disabled", fld.getAttribute("disabled"));
		
		if ("dcf.Label".equals(ic.getName())) {
			if (! input.hasNotEmptyAttribute("value") && fld.hasNotEmptyAttribute("value"))
				ic.withAttribute("data-value", fld.getAttribute("value"));
			
			if (input.hasNotEmptyAttribute("value")) {
				ic.withAttribute("data-value", input.getAttribute("value"));
				ic.removeAttribute("value");
			}
			
			if (ic.hasNotEmptyAttribute("data-value")) 
				ic.withText(ic.getAttribute("data-value"));
		}
		else {
			if (! input.hasNotEmptyAttribute("value") && fld.hasNotEmptyAttribute("value"))
				ic.withAttribute("value", fld.getAttribute("value"));
		}
		
		if (! input.hasNotEmptyAttribute("name") && fld.hasNotEmptyAttribute("name"))
			ic.withAttribute("name", fld.getAttribute("name"));
		
		if (! input.hasNotEmptyAttribute("placeholder") && fld.hasNotEmptyAttribute("placeholder"))
			ic.withAttribute("placeholder", fld.getAttribute("placeholder"));
		
		if (! input.hasNotEmptyAttribute("min") && fld.hasNotEmptyAttribute("min"))
			ic.withAttribute("min", fld.getAttribute("min"));
		
		if (! input.hasNotEmptyAttribute("max") && fld.hasNotEmptyAttribute("max"))
			ic.withAttribute("max", fld.getAttribute("max"));

		if (! input.hasNotEmptyAttribute("maxlength") && fld.hasNotEmptyAttribute("maxlength"))
			ic.withAttribute("maxlength", fld.getAttribute("maxlength"));

		if (input.hasNotEmptyAttribute("Name"))
			ic.withAttribute("data-dcf-name", input.getAttribute("Name"));
		else if (fld.hasNotEmptyAttribute("Name"))
			ic.withAttribute("data-dcf-name", fld.getAttribute("Name"));
		
		if (input.hasNotEmptyAttribute("Record"))
			ic.withAttribute("data-dcf-record", input.getAttribute("Record"));
		else if (fld.hasNotEmptyAttribute("Record"))
			ic.withAttribute("data-dcf-record", fld.getAttribute("Record"));
		
		if (input.hasNotEmptyAttribute("Required"))
			ic.withAttribute("data-dcf-required", input.getAttribute("Required"));
		else if (fld.hasNotEmptyAttribute("Required"))
			ic.withAttribute("data-dcf-required", fld.getAttribute("Required"));
		
		if ("true".equals(ic.getAttribute("data-dcf-required"))) {
			fld.withClass("dc-required");
			ic.attr("aria-required", "true");
		}
		
		if (input.hasNotEmptyAttribute("DataType"))
			ic.withAttribute("data-dcf-data-type", input.getAttribute("DataType"));
		else if (fld.hasNotEmptyAttribute("DataType"))
			ic.withAttribute("data-dcf-data-type", fld.getAttribute("DataType"));
		
		if (input.hasNotEmptyAttribute("Pattern"))
			ic.withAttribute("data-dcf-pattern", input.getAttribute("Pattern"));
		else if (fld.hasNotEmptyAttribute("Pattern"))
			ic.withAttribute("data-dcf-pattern", fld.getAttribute("Pattern"));
		
		if (input.hasNotEmptyAttribute("Size"))
			ic.withClass("dc-input-" + input.getAttribute("Size"));
		
		// copy attributes over, only if not the same object
		if (ic != input) {
			for (Entry<String, String> entry : input.getAttributes().entrySet()) {
				if (Character.isLowerCase(entry.getKey().charAt(0))) {
					ic.withAttribute(entry.getKey(), entry.getValue());
				}
			}
		}
		
		return ic;
	}
	
	static public InputControl tag() {
		InputControl el = new InputControl();
		el.setName("dcf.Text");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return InputControl.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		if (this.hasNotEmptyAttribute("data-dcf-data-type")) {
			// automatically require the form's data types
			this.getRoot(state).with(XElement.tag("Require")
					.withAttribute("Types", this.getAttribute("data-dcf-data-type")));
		}
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this
			.withAttribute("data-dc-enhance", "true")
			.withAttribute("data-dc-tag", this.getName());
		
		String opname = this.getName();
		
		this.setName("input");
		
		if ("dcf.Label".equals(opname))
			this.setName("label");
		
		if ("dcf.Select".equals(opname))
			this.setName("select");
		
		if ("dcf.TextArea".equals(opname)) {
			this.setName("textarea");
			this.withClass("dc-textarea");
		}
	}
}
