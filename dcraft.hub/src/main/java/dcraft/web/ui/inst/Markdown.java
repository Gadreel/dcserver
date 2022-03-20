package dcraft.web.ui.inst;

import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.locale.LocaleUtil;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.struct.FieldStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;
import dcraft.web.md.MarkdownUtil;
import dcraft.web.ui.UIUtil;
import dcraft.xml.XElement;
import dcraft.xml.XNode;

public class Markdown extends Base {
	static public Markdown tag() {
		Markdown el = new Markdown();
		el.setName("dc.Markdown");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Markdown.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		// Safe|Unsafe
		String mode = StackUtil.stringFromSource(state,"Mode", "Unsafe");

		String content = StackUtil.resolveValueToString(state, this.getValue());

		XElement root = MarkdownUtil.process(content, "safe".equals(mode.toLowerCase()));

		if (root == null) {
			Logger.warn("inline md error: ");
			OperationContext.getAsTaskOrThrow().clearExitCode();
		}
		else if (root != null) {
			this.clearChildren();

			// root is just a container and has no value
			this.replaceChildren(root);
		}
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this.setName("div");
    }
}
