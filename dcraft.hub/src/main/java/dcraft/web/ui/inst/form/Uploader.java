package dcraft.web.ui.inst.form;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
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
		Base grp = W3.tag("div")
			.withClass("dc-control dc-uploader");
		
		grp
			.withAttribute("id", this.fieldid)
			.withAttribute("data-dc-enhance", "true")
			.withAttribute("data-dc-tag", this.getName())
			.with(W3.tag("div")
					.withClass("dc-uploader-file")
					.with(W3.tag("input")
							.withAttribute("type", "file")
							.withAttribute("capture", "capture")
							.withAttribute("multiple", "multiple")
					)
			)
			.with(W3.tag("div")
					.withClass("dc-uploader-list-area dc-message dc-message-info")
					.with(
						W3.tag("div")
							.withClass("dc-uploader-list-header")
							.withText("Selected Files: ")
					)
					.with(
						W3.tag("div")
							.withClass("dc-uploader-listing")
					)
			);
		
		RadioControl.enhanceField(this, grp);

		this.with(grp);
		
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
}
