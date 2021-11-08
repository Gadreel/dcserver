package dcraft.db.proc.trigger;

import dcraft.db.proc.ITrigger;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.struct.BaseStruct;
import dcraft.struct.Struct;
import dcraft.util.TimeUtil;

public class AfterUserCreate implements ITrigger {
	@Override
	public boolean execute(TablesAdapter db, String table, String id, BaseStruct context) throws OperatingContextException {
		if ("dcUser".equals(table)) {
			db.setScalar(table, id, "dcCreated", TimeUtil.now());
		}
		
		return true;
	}
}
