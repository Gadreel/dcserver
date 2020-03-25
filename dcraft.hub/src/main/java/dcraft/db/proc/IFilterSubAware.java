package dcraft.db.proc;

import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.struct.RecordStruct;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

public interface IFilterSubAware extends IFilter {
	@Override
	default ExpressionResult check(TablesAdapter adapter, IVariableAware scope, String table, Object val) throws OperatingContextException {
		return this.check(adapter, scope, table, val, null);
	}

	ExpressionResult check(TablesAdapter adapter, IVariableAware scope, String table, Object val, Object subid) throws OperatingContextException;
}
