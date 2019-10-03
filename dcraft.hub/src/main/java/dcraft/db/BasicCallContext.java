package dcraft.db;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.service.ServiceRequest;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;

import java.math.BigDecimal;

public class BasicCallContext extends BasicRequestContext implements ICallContext {
	static public BasicCallContext of(DatabaseAdapter conn, OperationOutcomeStruct outcome) throws OperatingContextException {
		BasicCallContext ctx = new BasicCallContext();
		ctx.outcome = outcome;
		ctx.ntrfc = conn;
		ctx.tenant = OperationContext.getOrThrow().getTenant().getAlias();
		ctx.stamp = conn.getManger().allocateStamp(0);
		return ctx;
	}
	
	static public BasicCallContext of(DatabaseAdapter conn, OperationOutcomeStruct outcome, String tenant, BigDecimal stamp) {
		BasicCallContext ctx = new BasicCallContext();
		ctx.outcome = outcome;
		ctx.ntrfc = conn;
		ctx.tenant = tenant;
		ctx.stamp = stamp;
		return ctx;
	}
	
	protected OperationOutcomeStruct outcome = null;
	protected boolean fromRpc = false;

	@Override
	public OperationOutcomeStruct getOutcome() {
		return this.outcome;
	}

	@Override
	public boolean isFromRpc() {
		return this.fromRpc;
	}

	@Override
	public void setFromRpc(boolean v) { this.fromRpc = v; }

}
