package dcraft.db.proc.filter;

import dcraft.db.proc.BasicFilter;
import dcraft.db.proc.FilterResult;
import dcraft.db.proc.IFilter;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.time.BigDateTime;
import dcraft.struct.RecordStruct;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
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
	public FilterResult check(TablesAdapter adapter, Object val, BigDateTime when, boolean historical) throws OperatingContextException {
		// we have already returned this one
		if ((this.max > 0) && (this.count.get() >= this.max))
			return FilterResult.halt();
		
		if (this.nested != null) {
			FilterResult nres = this.nested.check(adapter, val, when, historical);
			
			if (nres.accepted)
				this.count.incrementAndGet();
			
			return nres;
		}
		
		this.count.incrementAndGet();
		
		return FilterResult.accepted();
	}
}
