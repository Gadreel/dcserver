package dcraft.db.proc.filter;

import dcraft.db.proc.BasicFilter;
import dcraft.db.proc.BasicFilterSubAware;
import dcraft.db.proc.ExpressionResult;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class UniqueSubAware extends BasicFilterSubAware {
	static public UniqueSubAware unique() {
		return new UniqueSubAware();
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

	public boolean contains(Object v) {
		return this.unique.contains(v);
	}

	public boolean addAll(Collection<Object> v) {
		return this.unique.addAll(v);
	}

	public boolean addAll(UniqueSubAware v) {
		return this.unique.addAll(v.unique);
	}

	public boolean add(Object v) {
		return this.unique.add(v);
	}

	// order is meaningless, but if you want just one of the elements
	public Object getOne() {
		for (Object o : this.unique)
			return o;
		
		return null;
	}
	
	@Override
	public ExpressionResult check(TablesAdapter adapter, IVariableAware scope, String table, Object val, Object subid) throws OperatingContextException {
		// we have already returned this one
		if (this.unique.contains(val))
			return ExpressionResult.REJECTED;
		
		ExpressionResult nres = this.nestOrAccept(adapter, scope, table, val, subid);
		
		if (nres.accepted)
			this.unique.add(val);
		
		return nres;
	}
}
