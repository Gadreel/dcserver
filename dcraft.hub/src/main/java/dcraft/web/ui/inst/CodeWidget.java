package dcraft.web.ui.inst;

import dcraft.script.work.InstructionWork;
import dcraft.script.inst.doc.Base;
import dcraft.xml.XElement;

public class CodeWidget extends Base {
	static public CodeWidget tag() {
		CodeWidget el = new CodeWidget();
		el.setName("dc.CodeWidget");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return CodeWidget.tag();
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) {
		this.withClass("dc-widget dc-widget-code");
		
		this.setName("div");
    }
}
