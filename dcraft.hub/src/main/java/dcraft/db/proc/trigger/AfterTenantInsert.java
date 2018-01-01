package dcraft.db.proc.trigger;

import dcraft.db.DbServiceRequest;
import dcraft.db.proc.IStoredProc;

import dcraft.hub.op.OperationOutcomeStruct;

public class AfterTenantInsert implements IStoredProc {
	@Override
	public void execute(DbServiceRequest task, OperationOutcomeStruct callback) {
		// TODO not used
		//  String id = task.getDataAsRecord().getFieldAsString("Id");
		
		callback.returnEmpty();
	}
}
