package dcraft.db.tables;

import dcraft.db.DatabaseAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;

import java.math.BigDecimal;

public class BasicTablesContext implements ITablesContext {
	static public BasicTablesContext of(DatabaseAdapter conn) throws OperatingContextException {
		BasicTablesContext ctx = new BasicTablesContext();
		ctx.ntrfc = conn;
		ctx.tenant = OperationContext.getOrThrow().getTenant().getAlias();
		ctx.stamp = conn.getManger().allocateStamp(0);
		return ctx;
	}
	
	static public BasicTablesContext of(DatabaseAdapter conn, String tenant, BigDecimal stamp) {
		BasicTablesContext ctx = new BasicTablesContext();
		ctx.ntrfc = conn;
		ctx.tenant = tenant;
		ctx.stamp = stamp;
		return ctx;
	}
	
	protected DatabaseAdapter ntrfc = null;
	protected BigDecimal stamp = null;
	protected String tenant = null;
	
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
}
