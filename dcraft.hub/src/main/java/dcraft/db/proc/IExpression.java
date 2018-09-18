package dcraft.db.proc;

import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.time.BigDateTime;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

public interface IExpression extends IFilter {
	ExpressionResult check(TablesAdapter adapter, IVariableAware scope, String table, String id) throws OperatingContextException;
	
	@Override
	default ExpressionResult check(TablesAdapter adapter, IVariableAware scope, String table, Object val) throws OperatingContextException {
		return this.check(adapter, scope, table, Struct.objectToString(val));
	}
}
