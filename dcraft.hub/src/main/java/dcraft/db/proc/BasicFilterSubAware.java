package dcraft.db.proc;

import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.struct.RecordStruct;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

abstract public class BasicFilterSubAware implements IFilterSubAware {
	protected IFilter nested = null;

	@Override
	public IFilter withNested(IFilter v) {
		this.nested = v;
		return this;
	}

	@Override
	public IFilter shiftNested(IFilter v) {
		v.withNested(this.nested);

		this.nested = v;
		return this;
	}

	/*
	public BasicFilter withNested(String table, WhereExpression v) throws OperatingContextException {
		if (v != null)
			this.nested = v.toFilter(table);
		
		return this;
	}
	*/

	public ExpressionResult nestOrAccept(TablesAdapter adapter, IVariableAware scope, String table, Object val, Object subid) throws OperatingContextException {
		if (this.nested != null) {
			if (this.nested instanceof IFilterSubAware)
				return ((IFilterSubAware) this.nested).check(adapter, scope, table, val, subid);

			return this.nested.check(adapter, scope, table, val);
		}

		return ExpressionResult.ACCEPTED;
	}

	@Override
	public void init(String table, RecordStruct filter) throws OperatingContextException {
	}
	
	@Override
	public void parse(IParentAwareWork state, XElement code, RecordStruct filter) {
	
	}
}
