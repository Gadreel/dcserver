package dcraft.web.ui.inst.misc;

import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.OperationsWork;
import dcraft.script.work.ReturnOption;
import dcraft.struct.RecordStruct;
import dcraft.struct.ScalarStruct;
import dcraft.struct.Struct;
import dcraft.tenant.SiteUtil;
import dcraft.util.StringUtil;
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

		RecordStruct schema = SiteUtil.getBaseSiteSchema();

		String name = StackUtil.stringFromSource(state, "Result");

		if (StringUtil.isNotEmpty(name)) {
			StackUtil.addVariable(state.getParent(), name, schema);
		}

		this.with(XText.ofRaw(schema.toPrettyString()));
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this.setName("script");
		this.attr("type", "application/ld+json");
	}
}
