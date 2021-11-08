package dcraft.db.proc.trigger;

import dcraft.db.proc.IStoredProc;

import dcraft.db.ICallContext;
import dcraft.db.proc.ITrigger;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.BaseStruct;
import dcraft.struct.Struct;

public class AfterTenantInsert implements ITrigger {
	@Override
	public boolean execute(TablesAdapter db, String table, String id, BaseStruct context) throws OperatingContextException {
		return true;
	}
}
