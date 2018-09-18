package dcraft.web.ui.inst.form;

import java.util.List;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.web.ui.inst.W3;
import dcraft.xml.XElement;

public class RadioGroup extends CoreField {
	static public RadioGroup tag() {
		RadioGroup el = new RadioGroup();
		el.setName("dcf.RadioGroup");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return RadioGroup.tag();
	}
	
	@Override
	public void addControl(InstructionWork state) throws OperatingContextException {
		Base grp = W3.tag("div")
			.withClass("dc-control", "dc-controlgroup-vertical");
		
		grp
			.withAttribute("id", this.fieldid)
			.withAttribute("data-dc-enhance", "true")
			.withAttribute("data-dc-tag", this.getName());
		
		RadioControl.enhanceField(this, grp);
		
		List<XElement> inputs = this.fieldinfo.selectAll("RadioButton");
		
		for (XElement el : inputs) 
			grp.with(RadioControl.fromRadioField(this, el));

		this.with(grp);
		
		/*
		 * 
				<dcf.RadioGroup Label="Radio:" Name="Internship">
				   <RadioButton Value="Internship" Label="Yes, a pre-Apprenticeship Internship only right now" />
				   <RadioButton Value="Either" Label="Yes, either a pre-Apprenticeship Internship or an Apprenticeship" />
				   <RadioButton Value="Apprenticeship" Label="No thanks, an Apprenticeship only please" />
				</dcf.RadioGroup>
				

				<div class="dc-control dc-controlgroup-vertical">
					<div class="dc-radio">
						<input type="radio" id="comm1" name="CertInterest" value="No" />
						<label for="comm1" class="dc-input-button"><i class="fa fa-circle" aria-hidden="true"></i> Not Interested</label>
					</div>
					<div class="dc-radio">
						<input type="radio" id="comm2" name="CertInterest" value="Yes" />
						<label for="comm2" class="dc-input-button"><i class="fa fa-circle" aria-hidden="true"></i> Interested</label>
					</div>
					<div class="dc-radio">
						<input type="radio" id="comm3" name="CertInterest" value="Maybe" />
						<label for="comm3" class="dc-input-button"><i class="fa fa-circle" aria-hidden="true"></i> Maybe</label>
					</div>
				</div>


				<dcf.HorizRadioGroup Label="Years Experience:" Name="GrazingYears" Required="true">
				   <RadioButton Value="LessThanFive" Label="0 - 5" />
				   <RadioButton Value="FiveToTen" Label="5 - 10" />
				   <RadioButton Value="TenToFifteen" Label="10 - 15" />
				   <RadioButton Value="FifteenToTwenty" Label="15 - 20" />
				   <RadioButton Value="TwentyOrMore" Label="20+" />
				</dcf.HorizRadioGroup>

				
				<div class="dc-control dc-controlgroup-horizontal">
					<div class="dc-radio">
						<input type="radio" id="comm1b" name="CertInterestb" value="No" />
						<label for="comm1b" class="dc-input-button"><i class="fa fa-circle" aria-hidden="true"></i> Not Interested</label>
					</div>
					<div class="dc-radio">
						<input type="radio" id="comm2b" name="CertInterestb" value="Yes" />
						<label for="comm2b" class="dc-input-button"><i class="fa fa-circle" aria-hidden="true"></i> Interested</label>
					</div>
					<div class="dc-radio">
						<input type="radio" id="comm3b" name="CertInterestb" value="Maybe" />
						<label for="comm3b" class="dc-input-button"><i class="fa fa-circle" aria-hidden="true"></i> Maybe</label>
					</div>
				</div>
				
				
		*/		
	}
}
