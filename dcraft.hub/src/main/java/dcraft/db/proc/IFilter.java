package dcraft.db.proc;

import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.time.BigDateTime;
import dcraft.script.StackUtil;
import dcraft.struct.RecordStruct;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

public interface IFilter {
	void init(String table, RecordStruct filter) throws OperatingContextException;
	ExpressionResult check(TablesAdapter adapter, IVariableAware scope, String table, Object val) throws OperatingContextException;
	void parse(IParentAwareWork state, XElement code, RecordStruct filter) throws OperatingContextException;
	IFilter withNested(IFilter v);
	IFilter shiftNested(IFilter v);
}
