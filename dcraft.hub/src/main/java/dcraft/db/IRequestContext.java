package dcraft.db;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;

import java.math.BigDecimal;
import java.util.ArrayList;

public interface IRequestContext {
	DatabaseAdapter getInterface();
	String getTenant() throws OperatingContextException;
	void pushTenant(String did);
	void popTenant();
	BigDecimal getStamp();
	boolean isReplicating();
	String getOp();
	Struct getData();
	RecordStruct getDataAsRecord();
	ListStruct getDataAsList();
}
