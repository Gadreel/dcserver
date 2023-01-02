package dcraft.db.request.schema;

import dcraft.db.DbServiceRequest;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.service.ServiceHub;
import dcraft.struct.BaseStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.NullStruct;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class DataUpdater extends RecordStruct {
	protected RecordStruct source = null;

	@Override
	public ReturnOption operation(StackWork state, XElement code) throws OperatingContextException {
		// TODO add support for select classes - parse like Where does
		if ("Update".equals(code.getName())) {
			String name = StackUtil.stringFromElement(state, code,"Field");
			
			if (StringUtil.isEmpty(name)) {
				Logger.error("Missing field name in record update");
				return ReturnOption.CONTINUE;
			}
			
			RecordStruct flds = this.getFieldAsRecord("Fields");
			
			if (flds == null) {
				flds = RecordStruct.record();
				
				this.with("Fields", flds);
			}
			
			RecordStruct data = RecordStruct.record()
					.with("UpdateOnly", true);
			
			if (code.hasNotEmptyAttribute("Lang"))
				data.with("Lang", StackUtil.stringFromElement(state, code,"Lang"));

			if (code.hasNotEmptyAttribute("Value")) {
				data.with("Data", StackUtil.refFromElement(state, code, "Value", true));
			}
			else if (code.hasNotEmptyAttribute("Source")) {
				String sourcefield = StackUtil.stringFromElementClean(state, code, "Source");

				if (StringUtil.isNotEmpty(sourcefield) && (this.source != null)) {
					boolean hasField = this.source.hasField(sourcefield);
					boolean isConditional = StackUtil.boolFromElement(state, code, "Conditional", false);
					BaseStruct value = this.source.getField(sourcefield);

					if (value != null) {
						data.with("Data", value);
					}
					else if (! isConditional || hasField) {
						data.with("Retired", true);
					}
					else if (isConditional) {
						return ReturnOption.CONTINUE;
					}
					else {
						Logger.error("Missing Update source field");
						return ReturnOption.CONTINUE;
					}
				}
				else {
					Logger.error("Missing Update value or source");
					return ReturnOption.CONTINUE;
				}
			}
			else {
				Logger.error("Missing Update value or source");
				return ReturnOption.CONTINUE;
			}

			if (code.hasNotEmptyAttribute("SubId")) {
				String subid = StackUtil.stringFromElement(state, code, "SubId");
				
				RecordStruct ret = flds.getFieldAsRecord(name);
				
				if (ret == null)
					ret = RecordStruct.record();
				
				ret.with(subid, data);
				
				flds.with(name, ret);
			}
			else {
				flds.with(name, data);
			}
			
			return ReturnOption.CONTINUE;
		}
		
		if ("Retire".equals(code.getName())) {
			String name = StackUtil.stringFromElement(state, code,"Field");
			
			if (StringUtil.isEmpty(name)) {
				Logger.error("Missing field name in record update");
				return ReturnOption.CONTINUE;
			}
			
			RecordStruct flds = this.getFieldAsRecord("Fields");
			
			if (flds == null) {
				flds = RecordStruct.record();
				
				this.with("Fields", flds);
			}
			
			RecordStruct data = RecordStruct.record()
					.with("Retired", true);
			
			if (code.hasNotEmptyAttribute("Lang"))
				data.with("Lang", StackUtil.stringFromElement(state, code,"Lang"));
			
			if (code.hasNotEmptyAttribute("SubId")) {
				String subid = StackUtil.stringFromElement(state, code, "SubId");
				
				RecordStruct ret = flds.getFieldAsRecord(name);
				
				if (ret == null)
					ret = RecordStruct.record();
				
				ret.with(subid, data);
				
				flds.with(name, ret);
			}
			else {
				flds.with(name, data);
			}
			
			return ReturnOption.CONTINUE;
		}

		// TODO deprecate "Set" and use "UpdateSet" instead
		if ("Set".equals(code.getName()) || "UpdateSet".equals(code.getName())) {
			String name = StackUtil.stringFromElement(state, code,"Field");

			if (StringUtil.isEmpty(name)) {
				Logger.error("Missing field name in record update list");
				return ReturnOption.CONTINUE;
			}

			ListStruct sets = this.getFieldAsList("Sets");

			if (sets == null) {
				sets = ListStruct.list();

				this.with("Sets", sets);
			}

			RecordStruct updateRecord = RecordStruct.record()
					.with("Field", name);

			if (code.hasNotEmptyAttribute("Values")) {
				updateRecord.with("Values", StackUtil.refFromElement(state, code, "Values", true));
			}
			else if (code.hasNotEmptyAttribute("Source")) {
				String sourcefield = StackUtil.stringFromElementClean(state, code, "Source");

				if (StringUtil.isNotEmpty(sourcefield) && (this.source != null)) {
					boolean hasField = this.source.hasField(sourcefield);
					boolean isConditional = StackUtil.boolFromElement(state, code, "Conditional", false);
					BaseStruct value = this.source.getField(sourcefield);

					if (value != null) {
						updateRecord.with("Values", value);
					}
					else if (! isConditional || hasField) {
						updateRecord.with("Values", ListStruct.list());		// empty the list
					}
					else if (isConditional) {
						return ReturnOption.CONTINUE;
					}
					else {
						Logger.error("Missing Update source field");
						return ReturnOption.CONTINUE;
					}
				}
				else {
					Logger.error("Missing Update value or source");
					return ReturnOption.CONTINUE;
				}
			}
			else {
				Logger.error("Missing Update value or source");
				return ReturnOption.CONTINUE;
			}

			sets.with(updateRecord);

			return ReturnOption.CONTINUE;
		}

		if ("Source".equals(code.getName())) {
			this.source = Struct.objectToRecord(StackUtil.refFromElement(state, code, "Value", true));

			return ReturnOption.CONTINUE;
		}

		return super.operation(state, code);
	}
}