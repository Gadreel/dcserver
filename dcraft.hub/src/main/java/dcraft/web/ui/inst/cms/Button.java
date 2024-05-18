package dcraft.web.ui.inst.cms;

import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.locale.LocaleUtil;
import dcraft.script.work.InstructionWork;
import dcraft.struct.FieldStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;
import dcraft.web.ui.UIUtil;
import dcraft.web.ui.inst.ICMSAware;
import dcraft.xml.XElement;
import dcraft.xml.XNode;

public class Button extends dcraft.web.ui.inst.Button implements ICMSAware {
	static public Button tag() {
		Button el = new Button();
		el.setName("dcm.Button");
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
		super.renderBeforeChildren(state);

		UIUtil.markIfEditable(state, this, "widget");
	}

	@Override
	public void renderAfterChildren(InstructionWork state) {
		this.withClass("dcm-button");

		super.renderAfterChildren(state);
	}

	@Override
	public boolean applyCommand(CommonPath path, XElement root, RecordStruct command) throws OperatingContextException {
		String cmd = command.getFieldAsString("Command");

		if ("UpdatePart".equals(cmd)) {
			// TODO check that the changes made are allowed
			RecordStruct params = command.getFieldAsRecord("Params");
			String area = params.selectAsString("Area");

			if ("Props".equals(area)) {
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
