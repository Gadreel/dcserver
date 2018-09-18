package dcraft.db.proc.trigger;

import dcraft.core.db.UserDataUtil;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.proc.ITrigger;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.ListStruct;

import java.util.List;

public class AfterUserRetire implements ITrigger {
	@Override
	public boolean execute(TablesAdapter db, String table, String id) throws OperatingContextException {
		if ("dcUser".equals(table)) {
			UserDataUtil.retireUserTrigger(db, id);
		}
		
		return true;
	}
}
