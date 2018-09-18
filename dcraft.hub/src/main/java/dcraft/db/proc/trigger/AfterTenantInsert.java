package dcraft.db.proc.trigger;

import dcraft.db.proc.IStoredProc;

import dcraft.db.ICallContext;
import dcraft.db.proc.ITrigger;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;

public class AfterTenantInsert implements ITrigger {
	@Override
	public boolean execute(TablesAdapter db, String table, String id) throws OperatingContextException {
		return true;
	}
}
