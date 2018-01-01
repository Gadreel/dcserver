package dcraft.web.ui.inst;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.script.inst.doc.Base;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class Button extends Base {
	static public Button tag() {
		Button el = new Button();
		el.setName("dc.Button");
		return el;
	}
	
	static public Button tag(String name) {
		Button el = new Button();
		el.setName(name);
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Button.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		String label = StackUtil.stringFromSource(state,"Label");
		String icon = StackUtil.stringFromSource(state,"Icon");
		String to = StackUtil.stringFromSource(state,"To", "#");
		String click = StackUtil.stringFromSource(state,"Click");
		String page = StackUtil.stringFromSource(state,"Page");
		
		this
				.withAttribute("href", StringUtil.isNotEmpty(page) ? page : to);
		
		if (StringUtil.isNotEmpty(label))
			this.withText(label);
		else if (StringUtil.isNotEmpty(icon))
			this.with(W3.tag("i").withAttribute("class", "fa " + icon));
		
		// Default, Primary, Selected (TODO Success, Info, Warning, Danger)
		String scope = StackUtil.stringFromSource(state,"Scope", "Default").toLowerCase();
		
		this.withClass("pure-button-" + scope);
		
		if (this.hasNotEmptyAttribute("To"))
			this.withAttribute("data-dc-to", to);
		
		if (StringUtil.isNotEmpty(page))
			this.withAttribute("data-dc-page", page);
		
		if (StringUtil.isNotEmpty(click))
			this.withAttribute("data-dc-click", click);
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) {
		this
				.withClass("pure-button", "dc-button")
				.withAttribute("data-dc-enhance", "true")
				.withAttribute("data-dc-tag", this.getName());
		
		if (this.getName().startsWith("dc.Wide"))
			this
					.withClass("pure-button-wide");		// TODO wide
    
		this.setName("a");
    }
}
