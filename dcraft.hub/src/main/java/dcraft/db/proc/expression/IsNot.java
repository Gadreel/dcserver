package dcraft.db.proc.expression;

import dcraft.db.proc.ExpressionResult;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.time.BigDateTime;

public class IsNot extends Is {
	@Override
	public ExpressionResult check(TablesAdapter adapter, IVariableAware scope, String table, String id) throws OperatingContextException {
		ExpressionResult res = super.check(adapter, scope, table, id);
		
		return ExpressionResult.of(! res.accepted, res.resume);
	}
}
