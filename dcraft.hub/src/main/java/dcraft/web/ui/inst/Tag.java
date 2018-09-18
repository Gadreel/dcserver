package dcraft.web.ui.inst;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class Tag extends Base {
	static public Tag tag() {
		Tag el = new Tag();
		el.setName("dc.Tag");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Tag.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		// Info, Warning, Danger
		String scope = StackUtil.stringFromSource(state,"Scope", "Default").toLowerCase();
		
		this.withClass("dc-tag dc-tag-" + scope);
    }
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this.setName("span");
	}
}
