package dcraft.web.ui.inst.form;

import java.util.Map.Entry;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.StackWork;
import dcraft.util.HexUtil;
import dcraft.script.inst.doc.Base;
import dcraft.web.ui.UIUtil;
import dcraft.web.ui.inst.W3;
import dcraft.xml.XElement;

public class RadioControl extends Base {
	static public void enhanceField(CoreField fld, XElement grp) {
		if (fld.hasNotEmptyAttribute("Name"))
			grp.withAttribute("data-dcf-name", fld.getAttribute("Name"));
		
		if (fld.hasNotEmptyAttribute("Record"))
			grp.withAttribute("data-dcf-record", fld.getAttribute("Record"));
		
		if (fld.hasNotEmptyAttribute("Required"))
			grp.withAttribute("data-dcf-required", fld.getAttribute("Required"));
		
		if ("true".equals(grp.getAttribute("data-dcf-required")))
			fld.withClass("dc-required");
		
		if (fld.hasNotEmptyAttribute("DataType"))
			grp.withAttribute("data-dcf-data-type", fld.getAttribute("DataType"));
		
		if (fld.hasNotEmptyAttribute("Pattern"))
			grp.withAttribute("data-dcf-pattern", fld.getAttribute("Pattern"));
	}
	
	static public XElement fromRadioField(InstructionWork state, CoreField fld, XElement input) throws OperatingContextException {
		RadioControl ic = (input instanceof RadioControl) ? (RadioControl) input : new RadioControl();

		ic.withAttribute("type", "radio");

		String value = "NA";

		if (input.hasNotEmptyAttribute("value"))
			value = StackUtil.stringFromElement(state, input, "value");
		else if (input.hasNotEmptyAttribute("Value"))
			value = StackUtil.stringFromElement(state, input, "Value");

		ic.withAttribute("value", value);

		if (! input.hasNotEmptyAttribute("id")) 
			ic.withAttribute("id", fld.fieldid + "-" + HexUtil.encodeHex(value));
		
		if (! input.hasNotEmptyAttribute("name") && fld.hasNotEmptyAttribute("Name"))
			ic.withAttribute("name", fld.getAttribute("Name"));
		
		if (! input.hasNotEmptyAttribute("readonly") && fld.hasNotEmptyAttribute("readonly"))
			ic.withAttribute("readonly", fld.getAttribute("readonly"));
		
		if (! input.hasNotEmptyAttribute("disabled") && fld.hasNotEmptyAttribute("disabled"))
			ic.withAttribute("disabled", fld.getAttribute("disabled"));
		
		// copy attributes over, only if not the same object TODO ???
		if (ic != input) {
			for (Entry<String, String> entry : input.getAttributes().entrySet()) {
				if (Character.isLowerCase(entry.getKey().charAt(0))) {
					ic.withAttribute(entry.getKey(), entry.getValue());
				}
			}
		}
		
		XElement circle = UIUtil.requireSvgIcon(fld, state, "fas", "circle", "fa5-1x");
		XElement check = UIUtil.requireSvgIcon(fld, state, "fas", "check", "fa5-1x");
		
		return W3.tag("div")
				.withClass("dc-radio")
				.with(ic)
				.with(W3.tag("label")
					.withClass("dc-input-button")
					.withAttribute("for", ic.getAttribute("id"))
					.with(circle)
					.with(check)
					.withText(input.getAttribute("Label"))
				);
	}
	
	static public RadioControl tag() {
		RadioControl el = new RadioControl();
		el.setName("dcf.RadioButton");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return RadioControl.tag();
	}
	
	public RadioControl() {
		super("dcf.RadioButton");
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this.setName("input");
	}
}
