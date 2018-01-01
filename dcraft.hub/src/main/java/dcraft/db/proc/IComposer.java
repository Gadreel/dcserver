package dcraft.db.proc;

import dcraft.db.DbServiceRequest;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.time.BigDateTime;
import dcraft.struct.RecordStruct;
import dcraft.struct.builder.ICompositeBuilder;

public interface IComposer {
	void writeField(DbServiceRequest task, ICompositeBuilder out,
					TablesAdapter db, String table, String id, BigDateTime when, RecordStruct field,
					boolean historical, boolean compact) throws OperatingContextException;
}
