package dcraft.db.proc;

import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.struct.Struct;

public interface IExpressionSubAware extends IFilterSubAware {
	ExpressionResult check(TablesAdapter adapter, IVariableAware scope, String table, String id, String subid) throws OperatingContextException;
	
	@Override
	default ExpressionResult check(TablesAdapter adapter, IVariableAware scope, String table, Object val, Object subid) throws OperatingContextException {
		return this.check(adapter, scope, table, Struct.objectToString(val), Struct.objectToString(subid));
	}
}
