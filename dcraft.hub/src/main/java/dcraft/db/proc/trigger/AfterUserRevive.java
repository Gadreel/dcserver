package dcraft.db.proc.trigger;

import dcraft.core.db.UserDataUtil;
import dcraft.db.proc.IStoredProc;
import dcraft.db.proc.ITrigger;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.struct.BaseStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.Struct;

import java.util.List;

public class AfterUserRevive implements ITrigger {
	@Override
	public boolean execute(TablesAdapter db, String table, String id, BaseStruct context) throws OperatingContextException {
		if ("dcUser".equals(table)) {
			UserDataUtil.reviveUserTrigger(db, id);
		}
		
		return true;
	}
}
