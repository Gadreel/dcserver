package dcraft.script.inst.db;

import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.inst.OperationsInstruction;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.OperationsWork;
import dcraft.script.work.ReturnOption;
import dcraft.struct.Struct;
import dcraft.xml.XElement;

public class UpdateTenant extends OperationsInstruction {
	static public UpdateTenant tag() {
		UpdateTenant el = new UpdateTenant();
		el.setName("dcdb.UpdateTenant");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return UpdateTenant.tag();
	}
	
	// TODO add error checking
	
	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		if (state.getState() == ExecuteState.READY) {
			String name = StackUtil.stringFromSource(state, "Name");
			
			Struct var = ResourceHub.getResources().getSchema().getType("dcdbUpdateTenant").create();
			
			if (var == null) {
				Logger.errorTr(520);
				return ReturnOption.DONE;
			}

			// in a loop this instruction may be run again, fresh and READY - check to see if so and then skip op prep
			if (this.find("Execute") == null) {
				this.with(XElement.tag("Execute")
						.withAttribute("Result", this.getAttribute("Result"))
				);
			}
			
			StackUtil.addVariable(state, name, var);
			
			((OperationsWork) state).setTarget(var);
			
			if (this.gotoTop(state))
				return ReturnOption.CONTINUE;
		}
		else {
			if (this.gotoNext(state, false))
				return ReturnOption.CONTINUE;
		}
		
		return ReturnOption.DONE;
	}

}
