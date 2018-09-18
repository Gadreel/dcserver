package dcraft.web.ui.inst.form;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.work.InstructionWork;
import dcraft.web.ui.inst.W3;
import dcraft.xml.XElement;

public class YesNo extends HorizRadioGroup {
	static public YesNo tag() {
		YesNo el = new YesNo();
		el.setName("dcf.YesNo");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return YesNo.tag();
	}
	
	@Override
	public void addControl(InstructionWork state) throws OperatingContextException {
		this.withAttribute("DataType", "Boolean");		//	always
		
		this.fieldinfo.with(
			W3.tag("RadioButton")
				.withAttribute("Label", "Yes")
				.withAttribute("Value", "true")
		).with(
			W3.tag("RadioButton")
				.withAttribute("Label", "No")
				.withAttribute("Value", "false")
		);
		
		/*
				<dcf.RadioGroup Label="Radio:" Name="Internship">
				   <RadioButton Value="true" Label="Yes" />
				   <RadioButton Value="false" Label="No" />
				</dcf.RadioGroup>
		*/		
		
		super.addControl(state);
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this
				.withAttribute("data-dc-enhance", "true")
				.withAttribute("data-dc-tag", "dcf.YesNo");
		
		super.renderAfterChildren(state);
	}
}
