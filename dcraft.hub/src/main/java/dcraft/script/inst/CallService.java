package dcraft.script.inst;

import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.OperationsWork;
import dcraft.script.work.ReturnOption;
import dcraft.struct.Struct;
import dcraft.xml.XElement;

public class CallService extends OperationsInstruction {
	static public CallService tag() {
		CallService el = new CallService();
		el.setName("dcs.CallService");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return CallService.tag();
	}
	
	/*
			<dcs.CallService Nam="aaa" Service="sss" Feature="bbb" Op="ccc" Params="$ggg" Result="ttt" />
	
	 */
	
	// TODO add error checking
	
	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		if (state.getState() == ExecuteState.READY) {
			String name = StackUtil.stringFromSource(state, "Name");
			
			Struct var = ResourceHub.getResources().getSchema().getType("dcsCallService").create();
			
			if (var == null) {
				Logger.errorTr(520);
				return ReturnOption.DONE;
			}
			
			if (this.hasNotEmptyAttribute("Service"))
				this.add(0, XElement.tag("SetField")
						.withAttribute("Name", "Service")
						.withAttribute("Value", this.getAttribute("Service"))
				);
			
			if (this.hasNotEmptyAttribute("Feature"))
				this.add(0, XElement.tag("SetField")
						.withAttribute("Name", "Feature")
						.withAttribute("Value", this.getAttribute("Feature"))
				);
			
			if (this.hasNotEmptyAttribute("Op"))
				this.add(0, XElement.tag("SetField")
						.withAttribute("Name", "Op")
						.withAttribute("Value", this.getAttribute("Op"))
				);
			
			if (this.hasNotEmptyAttribute("Params"))
				this.add(0, XElement.tag("SetField")
						.withAttribute("Name", "Params")
						.withAttribute("Value", this.getAttribute("Params"))
				);
			
			if (this.hasNotEmptyAttribute("Result"))
				this.with(XElement.tag("Execute")
						.withAttribute("Result", this.getAttribute("Result"))
				);
			
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
