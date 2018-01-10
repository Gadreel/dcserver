package dcraft.db.proc;

import java.util.function.Function;

import dcraft.db.DbServiceRequest;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.time.BigDateTime;
import dcraft.struct.RecordStruct;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

public interface ICollector {
	void collect(DbServiceRequest task, TablesAdapter db, String table, BigDateTime when, boolean historical, RecordStruct collector, IFilter filter) throws OperatingContextException;
	RecordStruct parse(IParentAwareWork state, XElement code) throws OperatingContextException;
}
