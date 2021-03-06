package dcraft.script.schema;

import dcraft.db.DbServiceRequest;
import dcraft.db.proc.ICollector;
import dcraft.db.proc.IExpression;
import dcraft.db.request.schema.Load;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.locale.LocaleUtil;
import dcraft.log.Logger;
import dcraft.schema.DbCollector;
import dcraft.schema.DbExpression;
import dcraft.script.StackUtil;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.service.ServiceHub;
import dcraft.service.ServiceRequest;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.NullStruct;
import dcraft.task.IParentAwareWork;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class CallService extends RecordStruct {
	@Override
	public ReturnOption operation(StackWork state, XElement code) throws OperatingContextException {
		if (state.getState() == ExecuteState.READY) {
			if ("Execute".equals(code.getName())) {
				String name = StackUtil.stringFromElement(state, code, "Result");
				
				state.setState(ExecuteState.RESUME);

				ServiceRequest request =  ServiceRequest.of(
						this.getFieldAsString("Service"),
						this.getFieldAsString("Feature"),
						this.getFieldAsString("Op")
				)
						.withData(this.getField("Params"))
						.withOutcome(
								new OperationOutcomeStruct() {
									@Override
									public void callback(Struct result) throws OperatingContextException {
										// not sure if this is useful
										if (result == null)
											result = NullStruct.instance;

										if (StringUtil.isNotEmpty(name))
											StackUtil.addVariable(state, name, result);

										OperationContext.getAsTaskOrThrow().resume();
									}
								});

				if (! StackUtil.boolFromElement(state, code, "IsFinal", false))
						request.withAsIncomplete();		// service doesn't have to be final data, that can be a separate app logic check

				ServiceHub.call(request);
				
				return ReturnOption.AWAIT;
			}
			
			return super.operation(state, code);
		}
		
		return ReturnOption.CONTINUE;
	}
}
