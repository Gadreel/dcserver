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
import dcraft.struct.BaseStruct;
import dcraft.xml.XElement;

public class RetireRecord extends OperationsInstruction {
	static public RetireRecord tag() {
		RetireRecord el = new RetireRecord();
		el.setName("dcdb.RetireRecord");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return RetireRecord.tag();
	}
	
	// TODO add error checking
	
	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		if (state.getState() == ExecuteState.READY) {
			String name = StackUtil.stringFromSource(state, "Name");

			BaseStruct var = ResourceHub.getResources().getSchema().getType("dcdbRetireRecord").create();
			
			if (var == null) {
				Logger.errorTr(520);
				return ReturnOption.DONE;
			}

			// in a loop this instruction may be run again, fresh and READY - check to see if so and then skip op prep
			if (this.find("Execute") == null) {
				if (this.hasNotEmptyAttribute("Id"))
					this.add(0, XElement.tag("SetField")
							.withAttribute("Name", "Id")
							.withAttribute("Value", this.getAttribute("Id"))
					);

				if (this.hasNotEmptyAttribute("Table"))
					this.add(0, XElement.tag("SetField")
							.withAttribute("Name", "Table")
							.withAttribute("Value", this.getAttribute("Table"))
					);

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