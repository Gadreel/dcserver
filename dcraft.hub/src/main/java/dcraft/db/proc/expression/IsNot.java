package dcraft.db.proc.expression;

import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.time.BigDateTime;

public class IsNot extends Is {
	@Override
	public boolean check(TablesAdapter adapter, String id, BigDateTime when, boolean historical) throws OperatingContextException {
		return ! super.check(adapter, id, when, historical);
	}
}
