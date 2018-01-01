package dcraft.script.inst.db;

import dcraft.db.request.query.CollectorField;
import dcraft.db.request.query.LoadRecordRequest;
import dcraft.db.request.query.SelectDirectRequest;
import dcraft.db.request.query.SelectFields;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.script.StackUtil;
import dcraft.script.inst.Instruction;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.ReturnOption;
import dcraft.service.ServiceHub;
import dcraft.struct.Struct;
import dcraft.struct.scalar.NullStruct;
import dcraft.task.TaskContext;
import dcraft.xml.XElement;

public class LoadRecord extends Instruction {
	static public LoadRecord tag() {
		LoadRecord el = new LoadRecord();
		el.setName("dcdb.LoadRecord");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return LoadRecord.tag();
	}
	
	/*
			<dcdb.LoadRecord Table="dcmCategory" Id="444" Result="catlookup">
				<Select Field="Id" />
				<Select Field="dcmTitle" As="Title" />
			</dcdb.LoadRecord>
	
	 */
	
	// TODO add error checking
	
	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		if (state.getState() == ExecuteState.READY) {
			String table = StackUtil.stringFromSource(state, "Table");
			String id = StackUtil.stringFromSource(state, "Id");
			String name = StackUtil.stringFromSource(state, "Result");
			
			SelectFields fields = SelectFields.select();
			
			for (XElement select : this.selectAll("Select")) {
				if (select.hasNotEmptyAttribute("As")) {
					fields.with(StackUtil.stringFromElement(state, select,"Field"),
							StackUtil.stringFromElement(state, select,"As"));
				}
				else {
					fields.with(StackUtil.stringFromElement(state, select,"Field"));
				}
			}
			
			for (XElement subselect : this.selectAll("SelectSubquery")) {
				SelectFields subfields = SelectFields.select();
				
				for (XElement select : subselect.selectAll("Select")) {
					if (select.hasNotEmptyAttribute("As")) {
						subfields.with(StackUtil.stringFromElement(state, select,"Field"),
								StackUtil.stringFromElement(state, select,"As"));
					}
					else {
						subfields.with(StackUtil.stringFromElement(state, select,"Field"));
					}
				}
				
				if (subselect.hasNotEmptyAttribute("As")) {
					fields.withSubquery(StackUtil.stringFromElement(state, subselect,"Field"),
							StackUtil.stringFromElement(state, subselect,"As"), subfields);
				}
				else {
					fields.withSubquery(StackUtil.stringFromElement(state, subselect,"Field"), subfields);
				}
				
			}
			
			LoadRecordRequest request = LoadRecordRequest.of(table)
					.withId(id)
					.withSelect(fields);
			
			// TODO check Query, should not need this in query either
			//TaskContext ctx = OperationContext.getAsTaskOrThrow();
			
			ServiceHub.call(request
					.toServiceRequest()
					.withOutcome(new OperationOutcomeStruct() {
						@Override
						public void callback(Struct result) throws OperatingContextException {
							//OperationContext.set(ctx);
							
							// not sure if this is useful
							if (result == null)
								result = NullStruct.instance;
							
							StackUtil.addVariable(state, name, result);
							
							state.setState(ExecuteState.RESUME);
							
							//ctx.resume();
							
							OperationContext.getAsTaskOrThrow().resume();
						}
					})
			);
			
			return ReturnOption.AWAIT;
		}
		
		return ReturnOption.CONTINUE;
	}
}
