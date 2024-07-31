package dcraft.mail.dcc.inst;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.ReturnOption;
import dcraft.struct.BaseStruct;
import dcraft.struct.scalar.AnyStruct;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class Param extends Base {
	static public Param tag() {
		Param el = new Param();
		el.setName("dcc.Param");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Param.tag();
	}

	public Param() {
		this.exclude = true;
	}
	
	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		// do not expand children, just set the variables on parent
		
		if (state.getState() == ExecuteState.READY) {
			String name = StackUtil.stringFromSource(state,"Name");
			
			if (StringUtil.isNotEmpty(name)) {
				BaseStruct val = StackUtil.refFromSource(state,"Value", true);
				
				if (val != null) {
					StackUtil.addVariable(state.getParent(), name, val);
				}
				else if (this.hasChildren()) {
					XElement copy = this.deepCopy();
					
					StackUtil.addVariable(state.getParent(), name, AnyStruct.of(copy));
				}
			}
		}
		
		return ReturnOption.DONE;
	}
}
