package dcraft.web.ui.inst;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.script.inst.doc.Base;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;
import dcraft.xml.XNode;

import java.util.ArrayList;
import java.util.List;

public class Panel extends Base {
	static public Panel tag() {
		Panel el = new Panel();
		el.setName("dc.Panel");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Panel.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		// the children will move into the body, so clear out our child list
		List<XNode> hiddenchildren = this.children;
		
		this.children = new ArrayList<>();
		
		String title = StackUtil.stringFromSource(state,"Title");
		String id = StackUtil.stringFromSource(state,"id");
		
		// Default, Primary, Success, Info, Warning, Danger
		String scope = StackUtil.stringFromSource(state,"Scope", "Primary").toLowerCase();
		
		this.withClass("dc-panel", "dc-panel-" + scope);
		
		this.with(W3.tag("div")
				.withAttribute("class", "dc-panel-heading")
				.with(W3.tag("h2").withText(title))
			);
		
		XElement bodyui = W3.tag("div")
				.withAttribute("class", "dc-panel-body");
		
		if (StringUtil.isNotEmpty(id))
			bodyui.withAttribute("id", id + "Body");

		if (hiddenchildren != null) {
			for (XNode n : hiddenchildren)
				bodyui.add(n);
		}
		
		this.with(bodyui);
    }
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this
				.withAttribute("data-dc-enhance", "true")
				.withAttribute("data-dc-tag", this.getName());
		
		this.setName("div");
	}
}
