package dcraft.db.request.schema;

import dcraft.db.DbServiceRequest;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.service.ServiceHub;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.NullStruct;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class Insert extends RecordStruct {
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
			
			if (code.hasNotEmptyAttribute("Value"))
				data.with("Data", StackUtil.refFromElement(state, code,"Value", true));
			
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
		
		if ("Execute".equals(code.getName())) {
			String name = StackUtil.stringFromElement(state, code, "Result");
			
			ServiceHub.call(DbServiceRequest.of("dcInsertRecord")
					.withData(this)
					.withOutcome(
						new OperationOutcomeStruct() {
							@Override
							public void callback(Struct result) throws OperatingContextException {
								// not sure if this is useful
								if (result == null)
									result = NullStruct.instance;
								
								if (StringUtil.isNotEmpty(name))
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
}
