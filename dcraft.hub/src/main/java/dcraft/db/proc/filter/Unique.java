package dcraft.db.proc.filter;

import dcraft.db.proc.BasicFilter;
import dcraft.db.proc.FilterResult;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.time.BigDateTime;

import java.util.HashSet;
import java.util.Set;

public class Unique extends BasicFilter {
	static public Unique unique() {
		return new Unique();
	}
	
	// TODO enhance by making this use ^dcTemp for large number of records
	// keyed by id
	protected Set<Object> unique = new HashSet<>();
	
	@Override
	public FilterResult check(TablesAdapter adapter, Object val, BigDateTime when, boolean historical) throws OperatingContextException {
		// we have already returned this one
		if (this.unique.contains(val))
			return FilterResult.rejected();
		
		if (this.nested != null) {
			FilterResult nres = this.nested.check(adapter, val, when, historical);
			
			if (nres.accepted)
				this.unique.add(val);
			
			return nres;
		}
		
		this.unique.add(val);
		
		return FilterResult.accepted();
	}
}
