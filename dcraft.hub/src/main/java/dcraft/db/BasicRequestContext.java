package dcraft.db;

import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.BaseStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class BasicRequestContext implements IRequestContext{
	static public BasicRequestContext ofDefaultDatabase() throws OperatingContextException {
		return BasicRequestContext.ofDatabase("default");
	}
	
	static public BasicRequestContext ofDatabase(String name) throws OperatingContextException {
		IConnectionManager connectionManager = ResourceHub.getResources().getDatabases().getDatabase(name);
		
		DatabaseAdapter conn = connectionManager.allocateAdapter();
		
		return BasicRequestContext.of(conn);
	}
	
	static public BasicRequestContext of(DatabaseAdapter conn) throws OperatingContextException {
		BasicRequestContext ctx = new BasicRequestContext();
		ctx.ntrfc = conn;
		ctx.tenant = OperationContext.getOrThrow().getTenant().getAlias();
		ctx.stamp = conn.getManger().allocateStamp(0);
		return ctx;
	}
	
	static public BasicRequestContext of(DatabaseAdapter conn, String tenant, BigDecimal stamp) {
		BasicRequestContext ctx = new BasicRequestContext();
		ctx.ntrfc = conn;
		ctx.tenant = tenant;
		ctx.stamp = stamp;
		return ctx;
	}
	
	protected DatabaseAdapter ntrfc = null;
	protected BigDecimal stamp = null;
	protected String tenant = null;
	protected BaseStruct data = null;
	protected String op = null;
	protected List<String> tenants = null;
	
	public BasicRequestContext withData(BaseStruct v) {
		this.data = v;
		return this;
	}
	
	// almost never needed, but available
	public BasicRequestContext withOp(String v) {
		this.op = v;
		return this;
	}
	
	@Override
	public DatabaseAdapter getInterface() {
		return this.ntrfc;
	}
	
	@Override
	public BigDecimal getStamp() {
		return this.stamp;
	}
	
	@Override
	public String getTenant() {
		return this.tenant;
	}
	
	@Override
	public String getOp() {
		return this.op;
	}
	
	@Override
	public boolean isReplicating() {
		return false;
	}
	
	@Override
	public BaseStruct getData() {
		return this.data;
	}
	
	@Override
	public ListStruct getDataAsList() {
		return Struct.objectToList(this.data);
	}
	
	@Override
	public RecordStruct getDataAsRecord() {
		return Struct.objectToRecord(this.data);
	}
	
	@Override
	public void pushTenant(String did) {
		if (this.tenants == null)
			this.tenants = new ArrayList<>();
		
		this.tenants.add(did);
	}
	
	@Override
	public void popTenant() {
		if (this.tenants != null)
			this.tenants.remove(this.tenants.size() - 1);
	}
}
