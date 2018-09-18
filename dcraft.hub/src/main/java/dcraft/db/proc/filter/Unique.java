package dcraft.db.proc.filter;

import dcraft.db.proc.BasicFilter;
import dcraft.db.proc.ExpressionResult;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.time.BigDateTime;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class Unique extends BasicFilter {
	static public Unique unique() {
		return new Unique();
	}
	
	// TODO enhance by making this use ^dcTemp for large number of records
	// keyed by id
	protected Set<Object> unique = new HashSet<>();
	
	public Collection<Object> getValues() {
		return unique;
	}
	
	public boolean isEmpty() {
		return this.unique.isEmpty();
	}
	
	// order is meaningless, but if you want just one of the elements
	public Object getOne() {
		for (Object o : this.unique)
			return o;
		
		return null;
	}
	
	@Override
	public ExpressionResult check(TablesAdapter adapter, IVariableAware scope, String table, Object val) throws OperatingContextException {
		// we have already returned this one
		if (this.unique.contains(val))
			return ExpressionResult.REJECTED;
		
		ExpressionResult nres = this.nestOrAccept(adapter, scope, table, val);
		
		if (nres.accepted)
			this.unique.add(val);
		
		return nres;
	}
}
