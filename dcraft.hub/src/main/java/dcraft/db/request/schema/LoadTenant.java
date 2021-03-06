package dcraft.db.request.schema;

import dcraft.db.Constants;
import dcraft.db.DbServiceRequest;
import dcraft.db.proc.IExpression;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.schema.DbExpression;
import dcraft.script.StackUtil;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.service.ServiceHub;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.NullStruct;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

public class LoadTenant extends RecordStruct {
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
			String name = StackUtil.stringFromElement(state, code, "Result");
			
			this
					.with("Table", "dcTenant")
					.with("Id", Constants.DB_GLOBAL_ROOT_RECORD);
			
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
