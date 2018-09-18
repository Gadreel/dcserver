package dcraft.db.proc;

import dcraft.db.DbServiceRequest;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.time.BigDateTime;
import dcraft.struct.RecordStruct;
import dcraft.struct.builder.ICompositeBuilder;

public interface IComposer {
	void writeField(ICompositeBuilder out, TablesAdapter db, IVariableAware scope, String table, String id, RecordStruct field,
					boolean compact) throws OperatingContextException;
}
