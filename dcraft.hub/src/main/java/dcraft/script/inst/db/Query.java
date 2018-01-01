package dcraft.script.inst.db;

import dcraft.db.request.query.CollectorField;
import dcraft.db.request.query.SelectDirectRequest;
import dcraft.db.request.query.SelectFields;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.script.StackUtil;
import dcraft.script.inst.Instruction;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.OperationsWork;
import dcraft.script.work.ReturnOption;
import dcraft.service.ServiceHub;
import dcraft.struct.Struct;
import dcraft.struct.scalar.NullStruct;
import dcraft.task.TaskContext;
import dcraft.xml.XElement;

public class Query extends Instruction {
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
			String table = StackUtil.stringFromSource(state, "Table");
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
			
			// TODO if ORDER is present then switch to general select
			
			SelectDirectRequest request = SelectDirectRequest.of(table)
					.withSelect(fields);
			
			// TODO support WHERE
			
			XElement collect = this.selectFirst("Collector");
			
			if (collect != null) {
				request.withCollector(
						CollectorField.collect()
								.withField(StackUtil.stringFromElement(state, collect,"Field"))
								// TODO also support multiple values - list?
								.withValues(StackUtil.refFromElement(state, collect,"Value"))
				);
			}
			
			//TaskContext ctx = OperationContext.getAsTaskOrThrow();
			
			state.setState(ExecuteState.RESUME);
			
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
							
							OperationContext.getAsTaskOrThrow().resume();
						}
					})
			);
			
			return ReturnOption.AWAIT;
		}
		
		return ReturnOption.CONTINUE;
	}
}
