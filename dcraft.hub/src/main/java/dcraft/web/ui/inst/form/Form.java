package dcraft.web.ui.inst.form;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.work.InstructionWork;
import dcraft.struct.Struct;
import dcraft.util.RndUtil;
import dcraft.util.StringUtil;
import dcraft.script.inst.doc.Base;
import dcraft.xml.XElement;

public class Form extends Base {
	static public Form tag() {
		Form el = new Form();
		el.setName("dcf.Form");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Form.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		String name = this.getAttribute("Name");
		
		// no name?  assign a temp name
		if (StringUtil.isEmpty(name))
			name = StringUtil.buildSimpleCode(12);
		
		this
			.withAttribute("data-dcf-name", name);
		
		// to avoid too many duplicates, lets not connect form to an auto assigned id
		//	.withAttribute("id", "frm" + name);
		
		if (! this.hasNotEmptyAttribute("id")) 
			this.withAttribute("id", "gen" + RndUtil.nextUUId());
		
		if (this.hasNotEmptyAttribute("RecordOrder"))
			this.withAttribute("data-dcf-record-order", this.getAttribute("RecordOrder"));
		
		if (this.hasNotEmptyAttribute("RecordType")) {
			this.withAttribute("data-dcf-record-type", this.getAttribute("RecordType"));

			// automatically require the form's data types
			this.getRoot(state).with(XElement.tag("Require")
					.withAttribute("Types", this.getAttribute("RecordType")));
		}

		if (this.hasNotEmptyAttribute("Focus"))
			this.withAttribute("data-dcf-focus", this.getAttribute("Focus"));

		if (this.hasNotEmptyAttribute("Prefix"))
			this.withAttribute("data-dcf-prefix", this.getAttribute("Prefix"));

		if (this.hasNotEmptyAttribute("AlwaysNew"))
			this.withAttribute("data-dcf-always-new", this.getAttribute("AlwaysNew").toLowerCase());
		
		if (this.hasNotEmptyAttribute("TitleFields"))
			this.withAttribute("data-dcf-title-fields", this.getAttribute("TitleFields"));
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this
			.withAttribute("data-dc-enhance", "true")
			.withAttribute("data-dc-tag", this.getName());
		
		this.withClass("dc-form");
		
		if (Struct.objectToBooleanOrFalse(this.getAttribute("Stacked")))
			this.withClass("dc-stacked-form");
		
		this.setName("form");
	}
	
	@Override
	public Form getForm(InstructionWork state) {
		return this;
	}
}
