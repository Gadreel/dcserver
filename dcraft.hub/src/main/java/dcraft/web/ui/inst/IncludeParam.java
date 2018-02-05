package dcraft.web.ui.inst;

import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.script.inst.doc.Out;
import dcraft.struct.Struct;
import dcraft.struct.scalar.AnyStruct;
import dcraft.xml.XElement;

// fragment file
public class IncludeParam extends Out {
	static public IncludeParam tag() {
		IncludeParam el = new IncludeParam();
		el.setName("dc.IncludeParam");
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
		
		Struct funcwrap = StackUtil.queryVariable(state, StackUtil.stringFromSource(state, "Name"));
		
		if ((funcwrap == null) || ! (funcwrap instanceof AnyStruct)) {
			Logger.error("Did not find UI parameter");
			return;
		}
		
		XElement pel = ((XElement) ((AnyStruct) funcwrap).getValue());
		
		if (pel != null) {
			this.replace(pel.deepCopy());
			
			// /examples/simple/simple-2
		}
	}
}
