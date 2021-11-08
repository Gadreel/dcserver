package dcraft.db.request.schema;

import dcraft.db.DbServiceRequest;
import dcraft.db.proc.ICollector;
import dcraft.db.proc.IExpression;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.schema.DbCollector;
import dcraft.schema.DbExpression;
import dcraft.script.StackUtil;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.service.ServiceHub;
import dcraft.struct.BaseStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.NullStruct;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

public class Load extends RecordStruct {
	@Override
	public ReturnOption operation(StackWork state, XElement code) throws OperatingContextException {
		// TODO add support for select classes - parse like Where does
		if ("Select".equals(code.getName())) {
			Load.addSelect(this, state, code);
			
			return ReturnOption.CONTINUE;
		}
		
		if ("SelectSubquery".equals(code.getName())) {
			Load.addSelect(this, state, code);
			
			return ReturnOption.CONTINUE;
		}
		
		if ("SelectGroup".equals(code.getName())) {
			Load.addSelect(this, state, code);
			
			return ReturnOption.CONTINUE;
		}
		
		if ("Execute".equals(code.getName())) {
			/*
			if (state.getStore().hasField("AwaitFlag")) {
				state.getStore().removeField("AwaitFlag");

				System.out.println("await: " + code.toLocalString());

				return ReturnOption.CONTINUE;
			}
			else {
				String name = StackUtil.stringFromElement(state, code, "Result");

				System.out.println("load: " + code.toLocalString());

				ServiceHub.call(DbServiceRequest.of("dcLoadRecord")
						.withData(this)
						.withOutcome(
								new OperationOutcomeStruct() {
									@Override
									public void callback(Struct result) throws OperatingContextException {
										// not sure if this is useful
										if (result == null)
											result = NullStruct.instance;

										StackUtil.addVariable(state, name, result);

										state.getStore().with("AwaitFlag", true);
										state.setState(ExecuteState.RESUME);

										OperationContext.getAsTaskOrThrow().resume();
									}
								})
				);
			}
			*/

			String name = StackUtil.stringFromElement(state, code, "Result");

			ServiceHub.call(DbServiceRequest.of("dcLoadRecord")
					.withData(this)
					.withOutcome(
						new OperationOutcomeStruct() {
							@Override
							public void callback(BaseStruct result) throws OperatingContextException {
								// not sure if this is useful
								if (result == null)
									result = NullStruct.instance;
								
								StackUtil.addVariable(state, name, result);

								state.withContinueFlag();
								
								OperationContext.getAsTaskOrThrow().resume();
							}
						})
			);

			return ReturnOption.AWAIT;
		}
		
		return super.operation(state, code);
	}
	
	static public void addSelect(RecordStruct target, IParentAwareWork state, XElement code) throws OperatingContextException {
		if ("Select".equals(code.getName())) {
			ListStruct selects = target.getFieldAsList("Select");
			
			if (selects == null) {
				selects = ListStruct.list();
				target.with("Select", selects);
			}
			
			RecordStruct field = RecordStruct.record();

			if (code.hasNotEmptyAttribute("Field"))
				field.with("Field", StackUtil.stringFromElement(state, code,"Field"));

			if (code.hasNotEmptyAttribute("Composer"))
				field.with("Composer", StackUtil.stringFromElement(state, code,"Composer"));
			
			if (code.hasNotEmptyAttribute("Filter"))
				field.with("Filter", StackUtil.stringFromElement(state, code,"Filter"));

			if (code.hasNotEmptyAttribute("As"))
				field.with("Name", StackUtil.stringFromElement(state, code,"As"));
			
			if (code.hasNotEmptyAttribute("Format"))
				field.with("Format", StackUtil.stringFromElement(state, code,"Format"));
			
			if (code.hasNotEmptyAttribute("SubId"))
				field.with("SubId", StackUtil.stringFromElement(state, code,"SubId"));
			
			if (code.hasNotEmptyAttribute("Full"))
				field.with("Full", StackUtil.boolFromElement(state, code,"Full"));
			
			if (code.hasNotEmptyAttribute("ForeignField"))
				field.with("ForeignField", StackUtil.stringFromElement(state, code,"ForeignField"));
			
			if (code.hasNotEmptyAttribute("Table"))
				field.with("Table", StackUtil.stringFromElement(state, code,"Table"));
			
			if (code.hasNotEmptyAttribute("KeyField"))
				field.with("KeyField", StackUtil.stringFromElement(state, code,"KeyField"));
			
			if (code.hasNotEmptyAttribute("Params"))
				field.with("Params", StackUtil.refFromElement(state, code,"Params"));
			
			selects.with(field);
			
			return;
		}
		
		if ("SelectSubquery".equals(code.getName())) {
			ListStruct selects = target.getFieldAsList("Select");
			
			if (selects == null) {
				selects = ListStruct.list();
				target.with("Select", selects);
			}
			
			RecordStruct field = RecordStruct.record()
					.with("Field", StackUtil.stringFromElement(state, code,"Field"));
			
			if (code.hasNotEmptyAttribute("As"))
				field.with("Name", StackUtil.stringFromElement(state, code,"As"));
			
			if (code.hasNotEmptyAttribute("Table"))
				field.with("Table", StackUtil.stringFromElement(state, code,"Table"));
			
			if (code.hasNotEmptyAttribute("KeyField"))
				field.with("KeyField", StackUtil.stringFromElement(state, code,"KeyField"));
			
			for (XElement child : code.selectAll("*"))
				Load.addSelect(field, state, child);
			
			selects.with(field);
			
			return;
		}
		
		if ("SelectGroup".equals(code.getName())) {
			ListStruct selects = target.getFieldAsList("Select");
			
			if (selects == null) {
				selects = ListStruct.list();
				target.with("Select", selects);
			}
			
			RecordStruct field = RecordStruct.record()
					.with("Field", StackUtil.stringFromElement(state, code,"Field"));
			
			if (code.hasNotEmptyAttribute("As"))
				field.with("Name", StackUtil.stringFromElement(state, code,"As"));
			
			field.with("KeyName", StackUtil.stringFromElement(state, code,"Key", "SubId"));
			
			for (XElement child : code.selectAll("*"))
				Load.addSelect(field, state, child);
			
			selects.with(field);
			
			return;
		}
	}
}
