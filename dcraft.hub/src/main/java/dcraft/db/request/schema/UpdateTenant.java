package dcraft.db.request.schema;

import dcraft.db.Constants;
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

public class UpdateTenant extends DataUpdater {
	protected RecordStruct source = null;

	@Override
	public ReturnOption operation(StackWork state, XElement code) throws OperatingContextException {
		if ("Execute".equals(code.getName())) {
			String name = StackUtil.stringFromElement(state, code, "Result");

			this
					.with("Table", "dcTenant")
					.with("Id", Constants.DB_GLOBAL_ROOT_RECORD);

			// cannot be empty
			if (this.isFieldEmpty("Fields"))
				this.with("Fields", RecordStruct.record());

			ServiceHub.call(DbServiceRequest.of("dcUpdateRecord")
					.withData(this)
					.withOutcome(
						new OperationOutcomeStruct() {
							@Override
							public void callback(BaseStruct result) throws OperatingContextException {
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
