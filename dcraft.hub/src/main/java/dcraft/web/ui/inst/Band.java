package dcraft.web.ui.inst;

import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.locale.LocaleUtil;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.script.inst.doc.Base;
import dcraft.struct.FieldStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;
import dcraft.web.ui.UIUtil;
import dcraft.xml.XElement;
import dcraft.xml.XNode;

import java.util.ArrayList;
import java.util.List;

public class Band extends Base implements ICMSAware {
	static public Band tag() {
		Band el = new Band();
		el.setName("dc.Band");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Band.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		// the children will move into the body, so clear out our child list
		List<XNode> hiddenchildren = this.children;
		
		this.children = new ArrayList<>();
		
		String id = StackUtil.stringFromSource(state,"id");
		
		// Full (aka None), Wide, Medium, Narrow
		String width = StackUtil.stringFromSource(state,"Width", "Wide").toLowerCase();
		
		this.withClass("dc-band");
		
		// None, Small, Medium, Large, Extra
		String pad = StackUtil.stringFromSource(state, "Pad", "none").toLowerCase();

		XElement bodyui = W3.tag("div")
				.withClass( "dc-band-wrapper", "dc-band-width-" + width, "dc-band-pad-" + pad);
		
		if (StringUtil.isNotEmpty(id))
			bodyui.withAttribute("id", id + "Body");
		
		if (hiddenchildren != null) {
			for (XNode n : hiddenchildren)
				bodyui.add(n);
		}
		
		this.with(bodyui);
		
		UIUtil.markIfEditable(state, this, "band");

		if (this.getAttributeAsBooleanOrFalse("Hidden")) {
			this.withClass("dc-band-hidden");
		}
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		boolean editable = UIUtil.canEdit(state, this);

		if (! this.getAttributeAsBooleanOrFalse("Hidden") || editable) {
			this
					.withAttribute("data-dc-enhance", "true")
					.withAttribute("data-dc-tag", this.getName());

			this.setName("div");
		}
		else {
			this.clearChildren();
			this.clearAttributes();
			this.withAttribute("data-dc-tag", this.getName());
			this.setName("span");
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
		}

		return false;
	}
}
