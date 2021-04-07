package dcraft.web.ui.inst.misc;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.tenant.SiteUtil;
import dcraft.xml.XElement;
import dcraft.xml.XText;

public class SiteSchema extends Base {
	static public SiteSchema tag() {
		SiteSchema el = new SiteSchema();
		el.setName("dcm.SiteSchema");
		return el;
	}

	@Override
	public XElement newNode() {
		return SiteSchema.tag();
	}

	public SiteSchema() {
		super("dcm.SiteSchema");
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		// TODO support overrides someday

		this.clearChildren();

		this.with(XText.ofRaw(SiteUtil.getBaseSiteSchema().toPrettyString()));
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this.setName("script");
		this.attr("type", "application/ld+json");
	}
}
