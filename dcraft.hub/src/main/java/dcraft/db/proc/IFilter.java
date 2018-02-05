package dcraft.db.proc;

import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.time.BigDateTime;
import dcraft.struct.RecordStruct;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

public interface IFilter {
	void init(String table, RecordStruct filter) throws OperatingContextException;
	ExpressionResult check(TablesAdapter adapter, Object val) throws OperatingContextException;
	void parse(IParentAwareWork state, XElement code, RecordStruct filter) throws OperatingContextException;
}
