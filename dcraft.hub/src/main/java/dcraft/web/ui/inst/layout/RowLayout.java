package dcraft.web.ui.inst.layout;

// - 1/1, 1/2, 1/3, 2/3, 1/4, 3/4, 1/5, 2/5, 3/5, 4/5

import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.util.StringUtil;
import dcraft.script.inst.doc.Base;
import dcraft.web.ui.UIUtil;
import dcraft.xml.XElement;

public class RowLayout extends Base {
	static public RowLayout tag() {
		RowLayout el = new RowLayout();
		el.setName("dc.RowLayout");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return RowLayout.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		String colpad = null;

		// get the last padding
		for (XElement ui : this.selectAll("dc.Column")) {
			colpad = StackUtil.stringFromElement(state,  ui,"Pad", "none").toLowerCase();
		}
		
		if (StringUtil.isNotEmpty(colpad)) {
			this.withClass("dc-layout-row-pad-" + colpad);
		}
		
		UIUtil.markIfEditable(state, this);
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		// Medium, Narrow
		String collapse = StackUtil.stringFromSource(state,"Collapse", "Medium").toLowerCase();
		
		this.withClass("pure-g dc-layout dc-layout-row dc-layout-collapse-" + collapse);
		
		this.setName("div");
	}
}
