package dcraft.db.proc.filter;

import dcraft.db.proc.BasicFilter;
import dcraft.db.proc.ExpressionResult;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.time.BigDateTime;

import java.util.concurrent.atomic.AtomicLong;

public class Max extends BasicFilter {
	static public Max max() {
		return new Max();
	}
	
	protected AtomicLong count = new AtomicLong();
	protected long max = 0;
	
	public Max withMax(long v) {
		this.max = v;
		return this;
	}
	
	public long getCount() {
		return this.count.get();
	}
	
	@Override
	public ExpressionResult check(TablesAdapter adapter, Object val) throws OperatingContextException {
		// we have already returned this one
		if ((this.max > 0) && (this.count.get() >= this.max))
			return ExpressionResult.HALT;
		
		ExpressionResult nres = this.nestOrAccept(adapter, val);
		
		if (nres.accepted)
			this.count.incrementAndGet();
		
		return nres;
	}
}
