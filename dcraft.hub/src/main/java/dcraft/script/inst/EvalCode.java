package dcraft.script.inst;

import dcraft.filestore.CommonPath;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.script.Script;
import dcraft.script.StackUtil;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.BlockWork;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.ReturnOption;
import dcraft.struct.BaseStruct;
import dcraft.struct.Struct;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;
import dcraft.xml.XNode;

import java.util.Collection;

public class EvalCode extends BlockInstruction {
	static public EvalCode tag() {
		EvalCode el = new EvalCode();
		el.setName("dcs.EvalCode");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return EvalCode.tag();
	}

	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		if (state.getState() == ExecuteState.READY) {
			// remove the children
			this.clearChildren();

			// find and include the fragment file
			if (this.hasAttribute("Code")) {
				BaseStruct codestruct = StackUtil.refFromSource(state, "Code");

				String code = Struct.objectToString(codestruct);

				Script script = Script.of("<dcs.Script>" + code + "</dcs.Script>");

				if (script != null) {
					XElement layout = script.getXml();

					Collection<XNode> children = layout.getChildren();

					for (XNode node : children) {
						this.with(node);
					}
				}
			}

			if (this.gotoTop(state))
				return ReturnOption.CONTINUE;
		}
		else {
			// each resume should go to next child instruction until we run out, then check logic
			if (! ((BlockWork) state).checkClearContinueFlag() && this.gotoNext(state, false))
				return ReturnOption.CONTINUE;
		}

//		String mode = StackUtil.stringFromSourceClean(state, "Mode", "Text");
//
//		if ("Text".equals(mode)) {
//			Base.cleanReferencesDeep(this, state);
//
//			XElement template = Struct.objectToXml(textdoc);
//
//			if (template != null) {
//				text = template.getText();
//			}
//		}

		this.clearChildren();

		// TODO support other modes such as XML and HTML, see SendEmail instruction for clues

		return ReturnOption.DONE;
	}

	@Override
	public InstructionWork createStack(IParentAwareWork state) {
		return BlockWork.of(state, this)
				.withControlAware(true);
	}
}