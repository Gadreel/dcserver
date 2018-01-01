package dcraft.web.ui.inst;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.script.inst.doc.Base;
import dcraft.struct.scalar.BooleanStruct;
import dcraft.xml.XElement;

public class Body extends Base {
	static public Body tag() {
		Body el = new Body();
		el.setName("dc.Body");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Body.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		W3 bodel = W3.tag("div");
		
		bodel.replace(this);
		
		bodel.setName("div");		// set after replace so name sticks
		bodel.withAttribute("id", "dcuiMain");
		
		BooleanStruct isdyn = (BooleanStruct) StackUtil.queryVariable(state, "PageIsDynamic");
		
		bodel.withClass(((isdyn != null) && isdyn.getValue()) ? "dcuiPage" : "dcuiLayer");
		
		this.clear();
		
		this.with(bodel);
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) {
		this.setName("body");
	}
}
