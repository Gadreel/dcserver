package dcraft.script.inst;

import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.OperationsWork;
import dcraft.script.work.ReturnOption;
import dcraft.struct.BaseStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;
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
			<dcs.CallService Name="aaa" Service="sss" Feature="bbb" Op="ccc" Params="$ggg" Result="ttt" />
	
	 */
	
	// TODO add error checking
	
	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		if (state.getState() == ExecuteState.READY) {
			String name = StackUtil.stringFromSource(state, "Name");

			BaseStruct var = ResourceHub.getResources().getSchema().getType("dcsCallService").create();
			
			if (var == null) {
				Logger.errorTr(520);
				return ReturnOption.DONE;
			}
			
			// in a loop this instruction may be run again, fresh and READY - check to see if so and then skip op prep
			if (this.find("Execute") == null) {
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
				
				if (this.hasNotEmptyAttribute("Op")) {
					String op = this.getAttribute("Op");
					
					if (op.contains(".")) {
						String[] parts = op.split("\\.");
						
						if (parts.length != 3) {
							Logger.error("Op path invalid");
							return ReturnOption.DONE;
						}
						
						this.add(0, XElement.tag("SetField")
								.withAttribute("Name", "Service")
								.withAttribute("Value", parts[0])
						);
						
						this.add(0, XElement.tag("SetField")
								.withAttribute("Name", "Feature")
								.withAttribute("Value", parts[1])
						);
						
						this.add(0, XElement.tag("SetField")
								.withAttribute("Name", "Op")
								.withAttribute("Value", parts[2])
						);
					}
					else {
						this.add(0, XElement.tag("SetField")
								.withAttribute("Name", "Op")
								.withAttribute("Value", op)
						);
					}
				}
				
				if (this.hasNotEmptyAttribute("Params")) {
					this.add(0, XElement.tag("SetField")
							.withAttribute("Name", "Params")
							.withAttribute("Value", this.getAttribute("Params"))
					);
				}
				else if (this.selectFirst("*") != null) {
					BaseStruct arg = null;
					String tname = "_temp-" + StringUtil.buildSecurityCode();

					if (this.selectFirst("Field") != null) {
						arg = RecordStruct.record();
						((RecordStruct)arg).expandQuickRecord(state, this);
					}
					else {
						arg = ListStruct.list();
						((ListStruct) arg).expandQuickList(state, this);
					}

					StackUtil.addVariable(state, tname, arg);

					this.add(0, XElement.tag("SetField")
							.withAttribute("Name", "Params")
							.withAttribute("Value", "$" + tname)
					);
				}

				if (this.hasNotEmptyAttribute("Result"))
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
