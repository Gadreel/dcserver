package dcraft.db.proc.filter;

import dcraft.db.proc.BasicExpression;
import dcraft.db.proc.ExpressionResult;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.struct.Struct;

public class ActiveConfirmed extends BasicExpression {
	@Override
	public ExpressionResult check(TablesAdapter adapter, IVariableAware scope, String table, String id) throws OperatingContextException {
		boolean confirmed = Struct.objectToBooleanOrFalse(adapter.getStaticScalar("dcUser", id, "dcConfirmed"));
		
		if (! confirmed)
			return ExpressionResult.REJECTED;
		
		/* TODO add state
		String state = Struct.objectToString(adapter.getStaticScalar("dcUser", id, "dcAccountState"));
		
		if (! "Active".equals(state))
			return ExpressionResult.REJECTED;
			*/

		return this.nestOrAccept(adapter, scope, table, id);
	}
}
