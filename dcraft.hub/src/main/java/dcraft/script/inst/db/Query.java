package dcraft.script.inst.db;

import dcraft.db.request.query.CollectorField;
import dcraft.db.request.query.SelectDirectRequest;
import dcraft.db.request.query.SelectFields;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.inst.Instruction;
import dcraft.script.inst.OperationsInstruction;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.OperationsWork;
import dcraft.script.work.ReturnOption;
import dcraft.service.ServiceHub;
import dcraft.struct.ScalarStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.NullStruct;
import dcraft.task.TaskContext;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class Query extends OperationsInstruction {
	static public Query tag() {
		Query el = new Query();
		el.setName("dcdb.Query");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Query.tag();
	}
	
	/*
			<dcdb.Query Table="dcmCategory" Result="catlookup">
				<Select Field="Id" />
				<Select Field="dcmTitle" As="Title" />
				
				<Collector Field="dcmAlias" Value="$category" />
			</dcdb.Query>
	
	 */
	
	// TODO add error checking


	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		if (state.getState() == ExecuteState.READY) {
			String name = StackUtil.stringFromSource(state, "Name");

			Struct var = ResourceHub.getResources().getSchema().getType("dcdbQuery").create();

			if (var == null) {
				Logger.errorTr(520);
				return ReturnOption.DONE;
			}

			// in a loop this instruction may be run again, fresh and READY - check to see if so and then skip op prep
			if (this.find("Execute") == null) {
				if (this.hasNotEmptyAttribute("Table"))
					this.add(0, XElement.tag("SetField")
							.withAttribute("Name", "Table")
							.withAttribute("Value", this.getAttribute("Table"))
					);

				if (this.hasNotEmptyAttribute("Compact"))
					this.add(0, XElement.tag("SetField")
							.withAttribute("Name", "Compact")
							.withAttribute("Value", this.getAttribute("Compact"))
					);

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
