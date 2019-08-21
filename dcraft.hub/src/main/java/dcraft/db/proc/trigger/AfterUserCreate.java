package dcraft.db.proc.trigger;

import dcraft.core.db.UserDataUtil;
import dcraft.db.proc.ITrigger;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.struct.Struct;
import dcraft.util.TimeUtil;

public class AfterUserCreate implements ITrigger {
	@Override
	public boolean execute(TablesAdapter db, String table, String id, Struct context) throws OperatingContextException {
		if ("dcUser".equals(table)) {
			db.setStaticScalar(table, id, "dcCreated", TimeUtil.now());
		}
		
		return true;
	}
}
