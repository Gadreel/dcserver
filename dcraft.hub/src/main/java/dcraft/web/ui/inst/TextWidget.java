package dcraft.web.ui.inst;

import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.locale.LocaleUtil;
import dcraft.log.DebugLevel;
import dcraft.log.Logger;
import dcraft.script.ScriptHub;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.script.inst.doc.Base;
import dcraft.struct.FieldStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
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
		String mode = StackUtil.stringFromSource(state,"Mode", "Safe");
		
		XElement root = UIUtil.translate(state, this, "safe".equals(mode.toLowerCase()));
		
		this.clearChildren();
		
		if (root != null) {
			// root is just a container and has no value
			this.replaceChildren(root);
		}
		else {
			// TODO add warning if guest, add symbol if CMS
		}

		//OperationContext.getOrThrow().setDebugLevel(DebugLevel.Trace);
		
		UIUtil.markIfEditable(state, this, "widget");
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
	
	@Override
	public boolean applyCommand(CommonPath path, XElement root, RecordStruct command) throws OperatingContextException {
		String cmd = command.getFieldAsString("Command");
		
		if ("UpdatePart".equals(cmd)) {
			// TODO check that the changes made are allowed - e.g. on TextWidget
			RecordStruct params = command.getFieldAsRecord("Params");
			String area = params.selectAsString("Area");
			
			if ("Props".equals(area)) {
				// TODO an Editor cannot change to Unsafe mode
				RecordStruct props = params.getFieldAsRecord("Properties");
				
				if (props != null) {
					for (FieldStruct fld : props.getFields()) {
						this.attr(fld.getName(), Struct.objectToString(fld.getValue()));
					}
				}
				
				return true;
			}
			
			if ("Content".equals(area)) {
				this.canonicalize();	// so all Tr's have a Locale
				
				String targetcontent = params.getFieldAsString("Content");
				String targetlocale = params.getFieldAsString("Locale");
				
				if (StringUtil.isEmpty(targetlocale))
					targetlocale = OperationContext.getOrThrow().getSite().getResources().getLocale().getDefaultLocale();
				else
					targetlocale = LocaleUtil.normalizeCode(targetlocale);
				
				for (int i = 0; i < this.getChildren().size(); i++) {
					XNode cn = this.getChild(i);
					
					if (! (cn instanceof XElement))
						continue;
					
					XElement cel = (XElement) cn;
					
					if (! "Tr".equals(cel.getName()))
						continue;
					
					// will have one, see canonicalize above
					String locale = LocaleUtil.normalizeCode(cel.getAttribute("Locale"));
					
					if (locale.equals(targetlocale)) {
						cel.value(targetcontent);
						return true;
					}
				}

				this.with(
						XElement.tag("Tr")
							.attr("Locale", targetlocale)
							.withCData(targetcontent)
				);
				
				return true;
			}
		}
		
		return false;
	}
}
