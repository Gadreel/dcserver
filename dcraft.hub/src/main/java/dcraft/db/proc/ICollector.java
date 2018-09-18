package dcraft.db.proc;

import dcraft.db.ICallContext;
import dcraft.db.IRequestContext;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.time.BigDateTime;
import dcraft.struct.RecordStruct;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

public interface ICollector {
	void collect(IRequestContext task, TablesAdapter db, IVariableAware scope, String table, RecordStruct collector, IFilter filter) throws OperatingContextException;
	RecordStruct parse(IParentAwareWork state, XElement code) throws OperatingContextException;
}
