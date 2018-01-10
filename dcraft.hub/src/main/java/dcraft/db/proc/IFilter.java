package dcraft.db.proc;

import dcraft.db.DbServiceRequest;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.time.BigDateTime;
import dcraft.struct.RecordStruct;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

import java.util.function.Function;

public interface IFilter {
	void init(String table, RecordStruct filter) throws OperatingContextException;
	// return true to stop
	FilterResult check(TablesAdapter adapter, Object val, BigDateTime when, boolean historical) throws OperatingContextException;
	void parse(IParentAwareWork state, XElement code, RecordStruct filter) throws OperatingContextException;
}
