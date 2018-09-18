package dcraft.web.ui.inst;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.locale.LocaleUtil;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.script.inst.doc.Base;
import dcraft.util.StringUtil;
import dcraft.web.md.MarkdownUtil;
import dcraft.web.ui.UIUtil;
import dcraft.xml.XElement;
import dcraft.xml.XNode;

public class TextWidget extends Base implements ICMSAware {
	static public TextWidget tag() {
		TextWidget el = new TextWidget();
		el.setName("dc.TextWidget");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return TextWidget.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		// Safe|Unsafe
		String mode = StackUtil.stringFromSource(state,"Mode", "Unsafe");
		
		XElement root = UIUtil.translate(state, this, "safe".equals(mode.toLowerCase()));
		
		this.clearChildren();
		
		if (root != null) {
			// root is just a container and has no value
			this.replaceChildren(root);
		}
		else {
			// TODO add warning if guest, add symbol if CMS
		}
		
		UIUtil.markIfEditable(state, this);
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this.withClass("dc-widget dc-widget-text")
			.withAttribute("data-dc-enhance", "true")
			.withAttribute("data-dc-tag", this.getName());
		
		this.setName("div");
    }
	
	@Override
	public void canonicalize() throws OperatingContextException {
		String deflocale = OperationContext.getOrThrow().getSite().getResources().getLocale().getDefaultLocale();
		
		for (int i = 0; i < this.getChildren().size(); i++) {
			XNode cn = this.getChild(i);
			
			if (! (cn instanceof XElement))
				continue;
			
			XElement cel = (XElement) cn;
			
			if (! "Tr".equals(cel.getName()))
				continue;
			
			String locale = LocaleUtil.normalizeCode(cel.getAttribute("Locale"));
			
			if (StringUtil.isEmpty(locale))
				cel.attr("Locale", deflocale);
		}
	}
}
