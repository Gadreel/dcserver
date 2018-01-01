package dcraft.db.proc;

import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.time.BigDateTime;
import dcraft.struct.RecordStruct;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

public interface IExpression {
	void init(String table, RecordStruct where) throws OperatingContextException;
	boolean check(TablesAdapter adapter, String id, BigDateTime when, boolean historical) throws OperatingContextException;
	void parse(IParentAwareWork state, XElement code, RecordStruct clause) throws OperatingContextException;
}
