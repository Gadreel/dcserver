package dcraft.web.ui.inst.form;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.web.ui.UIUtil;
import dcraft.web.ui.inst.W3;
import dcraft.xml.XElement;

public class Uploader extends CoreField {
	static public Uploader tag() {
		Uploader el = new Uploader();
		el.setName("dcf.Uploader");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Uploader.tag();
	}
	
	@Override
	public void addControl(InstructionWork state) throws OperatingContextException {
		XElement input = W3.tag("input")
				.withAttribute("id", this.fieldid)
				.withAttribute("data-dc-enhance", "true")
				.withAttribute("data-dc-tag", this.getName())
				.withAttribute("type", "file");
				//.withAttribute("capture", "capture");

		if (this.hasAttribute("accept"))
			input.attr("accept", this.attr("accept"));  // image/*;capture=camera

		if (this.hasAttribute("capture"))
			input.attr("capture", this.attr("capture"));

		if (! this.getAttributeAsBooleanOrFalse("Single"))
				input.withAttribute("multiple", "multiple");
		
		Base grp = W3.tag("div")
			.withClass("dc-control dc-uploader");
		
		grp
			.with(W3.tag("div")
					.withClass("dc-uploader-file")
					.with(input)
			)
			.with(W3.tag("div")
					.withClass("dc-uploader-list-area dc-message dc-message-info")
					.with(
						W3.tag("div")
							.withClass("dc-uploader-list-header")
							.withText(this.getAttributeAsBooleanOrFalse("Single") ? "{$_Tr.dcwWUploaderSelFile}: " : "{$_Tr.dcwWUploaderSelFiles}: ")
					)
					.with(
						W3.tag("div")
							.withClass("dc-uploader-listing")
					)
			);
		
		RadioControl.enhanceField(this, input);

		this.with(grp);
		
		UIUtil.requireIcon(this, state, "fas/times");
		UIUtil.requireIcon(this, state, "fas/pencil-alt");
		UIUtil.requireIcon(this, state, "fas/eye");
		
		/*
				<dcf.Uploader Label="Files" Name="Attachments" />
				
				<div class="dc-control dc-uploader">
					<div class="dc-uploader-file">
						<input type="file" />
					</div>
					<div class="dc-uploader-list">
					</div>
				</div>
		*/		
	}

	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		super.renderBeforeChildren(state);

		// TODO better way to do this
		// automatically require the form's data types
		this.getRoot(state).with(XElement.tag("Require")
				.withAttribute("Script", "/js/dc.transfer.js")
		);
	}
}
