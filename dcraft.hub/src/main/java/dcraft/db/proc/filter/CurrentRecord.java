package dcraft.db.proc.filter;

import dcraft.db.proc.BasicExpression;
import dcraft.db.proc.ExpressionResult;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;

public class CurrentRecord extends BasicExpression {
	static public CurrentRecord current() {
		return new CurrentRecord();
	}

	@Override
	public ExpressionResult check(TablesAdapter adapter, IVariableAware scope, String table, String id) throws OperatingContextException {
		boolean confirmed = adapter.isCurrent(table, id);
		
		if (! confirmed)
			return ExpressionResult.REJECTED;

		return this.nestOrAccept(adapter, scope, table, id);
	}
}
