package dcraft.web.ui.inst.layout;

// - grid (flex)

import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.script.inst.doc.Base;
import dcraft.xml.XElement;
import dcraft.xml.XNode;

public class GridLayout extends Base {
	static public GridLayout tag() {
		GridLayout el = new GridLayout();
		el.setName("dc.RowLayout");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return GridLayout.tag();
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this.withClass("pure-g dc-layout dc-layout-grid");
		
		String size = StackUtil.stringFromSource(state,"Size", "1-3");
		
		if (this.children != null) {
			for (XNode node : this.children) {
				if (node instanceof XElement) {
					XElement ui = (XElement) node;
					
					String colsize = StackUtil.stringFromElement(state,  ui,"Column.Size", size);
					
					if (ui instanceof Base)
						((Base) ui).withClass("pure-u", "pure-u-" + colsize);
					else
						ui.setAttribute("class", ui.getAttribute("class", "") + " pure-u pure-u-" + colsize);
				}
			}
		}
		
		this.setName("div");
	}
}
