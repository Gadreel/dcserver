package dcraft.db.request.schema;

import dcraft.db.DbServiceRequest;
import dcraft.db.proc.IExpression;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.schema.DbExpression;
import dcraft.script.StackUtil;
import dcraft.script.work.ReturnOption;
import dcraft.service.ServiceHub;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.NullStruct;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

public class Query extends RecordStruct {
	@Override
	public ReturnOption operation(IParentAwareWork state, XElement code) throws OperatingContextException {
		// TODO add support for select classes - parse like Where does
		if ("Select".equals(code.getName())) {
			Query.addSelect(this, state, code);
			
			return ReturnOption.CONTINUE;
		}
		
		if ("SelectSubquery".equals(code.getName())) {
			Query.addSelect(this, state, code);
			
			return ReturnOption.CONTINUE;
		}
		
		if ("Where".equals(code.getName())) {
			RecordStruct where = this.getFieldAsRecord("Where");
			
			if (where == null) {
				where = RecordStruct.record()
					.with("Expression", "And")
					.with("Children", ListStruct.list());
				
				this.with("Where", where);
			}
			
			Query.addWhere(where.getFieldAsList("Children"), state, code);
			
			return ReturnOption.CONTINUE;
		}
		
		if ("Collector".equals(code.getName())) {
			// TODO add support for `Func` attribute - parse like Where does
			RecordStruct collector = RecordStruct.record()
					.with("Field", StackUtil.stringFromElement(state, code, "Field"))
					.with("SubId", StackUtil.stringFromElement(state, code, "SubId"))
					.with("From", StackUtil.stringFromElement(state, code, "From"))
					.with("To", StackUtil.stringFromElement(state, code, "To"));
			
			if (code.hasNotEmptyAttribute("Values")) {
				String values = code.getAttribute("Values");
				
				if (values.startsWith("$")) {
					// this should be a ListStruct
					collector.with("Values", StackUtil.refFromElement(state, code, "Values"));
				}
				else {
					collector.with("Values", ListStruct.list(StackUtil.stringFromElement(state, code, "Values").split(",")));
				}
			}
				
			this.with("Collector", collector);
			
			return ReturnOption.CONTINUE;
		}
		
		if ("Execute".equals(code.getName())) {
			String name = StackUtil.stringFromElement(state, code, "Result");
			
			//if (state instanceof StackWork)
			//	((StackWork)state).setState(ExecuteState.DONE);
			
			ServiceHub.call(DbServiceRequest.of("dcSelectDirect")
					.withData(this)
					.withOutcome(
						new OperationOutcomeStruct() {
							@Override
							public void callback(Struct result) throws OperatingContextException {
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
		
		return super.operation(state, code);
	}
	
	static public RecordStruct createWhereField(IParentAwareWork state, XElement code) throws OperatingContextException {
		RecordStruct ret = RecordStruct.record()
				.with("Field", StackUtil.stringFromElement(state, code, "Field"));
		
		if (code.hasNotEmptyAttribute("SubId"))
			ret.with("SubId", StackUtil.stringFromElement(state, code,"SubId"));
		
		if (code.hasNotEmptyAttribute("Format"))
			ret.with("Format", StackUtil.stringFromElement(state, code,"Format"));
		
		if (code.hasNotEmptyAttribute("Composer"))
			ret.with("Composer", StackUtil.stringFromElement(state, code,"Composer"));
		
		return ret;
	}
	
	static public RecordStruct createWhereValue(IParentAwareWork state, XElement code, String name) throws OperatingContextException {
		return RecordStruct.record()
				.with("Value", StackUtil.stringFromElement(state, code, name));
	}
	
	static public void addWhere(ListStruct children, IParentAwareWork state, XElement code) throws OperatingContextException {
		for (XElement child : code.selectAll("*")) {
			DbExpression proc = ResourceHub.getResources().getSchema().getDbExpression(child.getName());
			
			if (proc == null) {
				Logger.warn("Missing expression: " + proc);
				continue;
			}
			
			String spname = proc.execute;		// TODO find class name for request.getOp()
			
			IExpression sp = (IExpression) ResourceHub.getResources().getClassLoader().getInstance(spname);
			
			if (sp == null) {
				Logger.warn("Cannot create expression: " + proc.name);
				continue;
			}
			
			RecordStruct clause = RecordStruct.record()
					.with("Expression", proc.name);
			
			if (code.hasNotEmptyAttribute("Locale"))
				clause.with("Locale", StackUtil.stringFromElement(state, code,"Locale"));
			
			sp.parse(state, child, clause);
			
			children.with(clause);
		}
	}
	
	static public void addSelect(RecordStruct target, IParentAwareWork state, XElement code) throws OperatingContextException {
		if ("Select".equals(code.getName())) {
			ListStruct selects = target.getFieldAsList("Select");
			
			if (selects == null) {
				selects = ListStruct.list();
				target.with("Select", selects);
			}
			
			RecordStruct field = RecordStruct.record()
					.with("Field", StackUtil.stringFromElement(state, code,"Field"));
			
			if (code.hasNotEmptyAttribute("As"))
				field.with("Name", StackUtil.stringFromElement(state, code,"As"));
			
			if (code.hasNotEmptyAttribute("Format"))
				field.with("Format", StackUtil.stringFromElement(state, code,"Format"));
			
			if (code.hasNotEmptyAttribute("SubId"))
				field.with("SubId", StackUtil.stringFromElement(state, code,"SubId"));
			
			if (code.hasNotEmptyAttribute("Full"))
				field.with("Full", StackUtil.boolFromElement(state, code,"Full"));
			
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
			
			for (XElement child : code.selectAll("*"))
				Query.addSelect(field, state, child);
			
			selects.with(field);
			
			return;
		}
	}
}
