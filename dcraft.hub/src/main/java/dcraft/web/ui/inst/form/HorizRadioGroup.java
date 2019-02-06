package dcraft.web.ui.inst.form;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.web.ui.inst.W3;
import dcraft.xml.XElement;

import java.util.List;

public class HorizRadioGroup extends CoreField {
	static public HorizRadioGroup tag() {
		HorizRadioGroup el = new HorizRadioGroup();
		el.setName("dcf.HorizRadioGroup");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return HorizRadioGroup.tag();
	}
	
	@Override
	public void addControl(InstructionWork state) throws OperatingContextException {
		Base grp = W3.tag("div")
			.withClass("dc-control", "dc-controlgroup-horizontal");
		
		grp
			.withAttribute("id", this.fieldid)
			.withAttribute("data-dc-enhance", "true")
			.withAttribute("data-dc-tag", this.getName());
		
		RadioControl.enhanceField(this, grp);
		
		List<XElement> inputs = this.fieldinfo.selectAll("RadioButton");
		
		for (XElement el : inputs) 
			grp.with(RadioControl.fromRadioField(state,this, el));

		this.with(grp);
		
		/*
		 * 
				<dcf.RadioGroup Label="Radio:" Name="Internship">
				   <RadioButton Value="Internship" Label="Yes, a pre-Apprenticeship Internship only right now" />
				   <RadioButton Value="Either" Label="Yes, either a pre-Apprenticeship Internship or an Apprenticeship" />
				   <RadioButton Value="Apprenticeship" Label="No thanks, an Apprenticeship only please" />
				</dcf.RadioGroup>
				

				<dcf.HorizRadioGroup Label="Years Experience:" Name="GrazingYears" Required="true">
				   <RadioButton Value="LessThanFive" Label="0 - 5" />
				   <RadioButton Value="FiveToTen" Label="5 - 10" />
				   <RadioButton Value="TenToFifteen" Label="10 - 15" />
				   <RadioButton Value="FifteenToTwenty" Label="15 - 20" />
				   <RadioButton Value="TwentyOrMore" Label="20+" />
				</dcf.HorizRadioGroup>
		*/
	}
}
