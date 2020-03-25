package dcraft.db.proc.filter;

import dcraft.db.proc.BasicExpression;
import dcraft.db.proc.BasicExpressionSubAware;
import dcraft.db.proc.ExpressionResult;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;

public class CurrentRecordSubAware extends BasicExpressionSubAware {
	static public CurrentRecordSubAware current() {
		return new CurrentRecordSubAware();
	}

	@Override
	public ExpressionResult check(TablesAdapter adapter, IVariableAware scope, String table, String id, String subid) throws OperatingContextException {
		boolean confirmed = adapter.isCurrent(table, id);
		
		if (! confirmed)
			return ExpressionResult.REJECTED;

		return this.nestOrAccept(adapter, scope, table, id, subid);
	}
}
