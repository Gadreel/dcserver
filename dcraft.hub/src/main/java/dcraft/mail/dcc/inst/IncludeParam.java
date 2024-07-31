package dcraft.mail.dcc.inst;

import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.inst.doc.Out;
import dcraft.script.work.InstructionWork;
import dcraft.struct.BaseStruct;
import dcraft.struct.scalar.AnyStruct;
import dcraft.xml.XElement;

// fragment file
public class IncludeParam extends Out {
	static public IncludeParam tag() {
		IncludeParam el = new IncludeParam();
		el.setName("dcc.IncludeParam");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return IncludeParam.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		super.renderBeforeChildren(state);
		
		// remove the children
		this.clearChildren();

		BaseStruct funcwrap = StackUtil.queryVariable(state, StackUtil.stringFromSource(state, "Name"));

		if (funcwrap == null) {
			funcwrap = StackUtil.refFromSource(state, "Ref");
		}

		XElement pel = null;

		if (funcwrap instanceof XElement) {
			pel = (XElement) funcwrap;
		}
		else if (funcwrap instanceof AnyStruct) {
			pel = ((XElement) ((AnyStruct) funcwrap).getValue());
		}

		if (pel != null) {
			this.replace(pel.deepCopy());
			
			// /examples/simple/simple-2
		}
		else {
			Logger.error("Did not find UI parameter");
		}
	}
}
