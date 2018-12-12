package dcraft.db.proc.expression;

import dcraft.db.proc.ExpressionResult;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.time.BigDateTime;

public class GreaterThan extends TwoExpression {
	@Override
	public ExpressionResult check(TablesAdapter adapter, IVariableAware scope, String table, String id) throws OperatingContextException {
		return (this.compare(adapter, id) == 1) ? this.nestOrAccept(adapter, scope, table, id) : ExpressionResult.REJECTED;
	}
}
