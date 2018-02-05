package dcraft.db.proc.expression;

import dcraft.db.proc.ExpressionResult;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.time.BigDateTime;

public class IsNot extends Is {
	@Override
	public ExpressionResult check(TablesAdapter adapter, String id) throws OperatingContextException {
		return super.check(adapter, id);
	}
}
