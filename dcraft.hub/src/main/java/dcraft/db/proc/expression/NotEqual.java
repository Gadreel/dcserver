package dcraft.db.proc.expression;

import dcraft.db.proc.ExpressionResult;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.time.BigDateTime;

public class NotEqual extends TwoExpression {
	@Override
	public ExpressionResult check(TablesAdapter adapter, String id) throws OperatingContextException {
		return (this.compare(adapter, id) != 0) ? ExpressionResult.ACCEPTED : ExpressionResult.REJECTED;
	}
}
