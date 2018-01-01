package dcraft.web.ui.inst;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.script.inst.doc.Base;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class Callout extends Base {
	static public Callout tag() {
		Callout el = new Callout();
		el.setName("dc.Callout");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Callout.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		// Info, Warning, Danger
		String scope = StackUtil.stringFromSource(state,"Scope", "Info").toLowerCase();
		
		this.withClass("dc-callout dc-callout-" + scope);
		
		String title = StackUtil.stringFromSource(state,"Title");
		
		if (StringUtil.isNotEmpty(title))
			this.add(0, W3.tag("h4")
				.withText(title)
			);
    }
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this.setName("div");
	}
}
