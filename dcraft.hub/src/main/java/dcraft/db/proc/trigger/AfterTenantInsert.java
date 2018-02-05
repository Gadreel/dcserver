package dcraft.db.proc.trigger;

import dcraft.db.proc.IStoredProc;

import dcraft.db.ICallContext;
import dcraft.hub.op.OperationOutcomeStruct;

public class AfterTenantInsert implements IStoredProc {
	@Override
	public void execute(ICallContext task, OperationOutcomeStruct callback) {
		// TODO not used
		//  String id = task.getDataAsRecord().getFieldAsString("Id");
		
		callback.returnEmpty();
	}
}
