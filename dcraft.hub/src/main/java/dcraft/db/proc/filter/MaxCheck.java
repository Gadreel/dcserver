package dcraft.db.proc.filter;

import dcraft.db.proc.BasicFilter;
import dcraft.db.proc.ExpressionResult;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;

import java.util.concurrent.atomic.AtomicLong;

public class MaxCheck extends BasicFilter {
	static public MaxCheck max() {
		return new MaxCheck();
	}
	
	protected AtomicLong count = new AtomicLong();
	protected long max = 0;
	
	public MaxCheck withMax(long v) {
		this.max = v;
		return this;
	}
	
	public long getCount() {
		return this.count.get();
	}
	
	@Override
	public ExpressionResult check(TablesAdapter adapter, IVariableAware scope, String table, Object val) throws OperatingContextException {
		// we have already returned this one
		if ((this.max > 0) && (this.count.get() >= this.max))
			return ExpressionResult.HALT;
		
		ExpressionResult nres = this.nestOrAccept(adapter, scope, table, val);
		
		this.count.incrementAndGet();
		
		return nres;
	}
}
