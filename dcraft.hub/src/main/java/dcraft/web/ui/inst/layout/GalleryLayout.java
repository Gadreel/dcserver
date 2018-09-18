package dcraft.web.ui.inst.layout;

// - grid (flex)

import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.script.inst.doc.Base;
import dcraft.web.ui.UIUtil;
import dcraft.xml.XElement;
import dcraft.xml.XNode;

public class GalleryLayout extends Base {
	static public GalleryLayout tag() {
		GalleryLayout el = new GalleryLayout();
		el.setName("dc.GalleryLayout");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return GalleryLayout.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		UIUtil.markIfEditable(state, this);
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this
				.withClass("pure-g dc-layout dc-layout-gallery")
				.attr("role", "list");
		
		String size = StackUtil.stringFromSource(state,"Size", "1-3");
		
		if (this.children != null) {
			for (XNode node : this.children) {
				if (node instanceof XElement) {
					XElement ui = (XElement) node;

					ui.attr("role", "listitem");

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
