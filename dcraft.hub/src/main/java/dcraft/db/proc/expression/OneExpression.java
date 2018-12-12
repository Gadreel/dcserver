package dcraft.db.proc.expression;

import dcraft.db.proc.ExpressionResult;
import dcraft.db.proc.IExpression;
import dcraft.db.proc.IFilter;
import dcraft.db.request.schema.Query;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

abstract public class OneExpression implements IExpression {
	protected ExpressionUtil.FieldInfo fieldInfo = null;
	protected String lang = null;
	protected String table = null;

	protected IFilter nested = null;

	@Override
	public IFilter withNested(IFilter v) {
		this.nested = v;
		return this;
	}

	public ExpressionResult nestOrAccept(TablesAdapter adapter, IVariableAware scope, String table, Object val) throws OperatingContextException {
		if (this.nested != null)
			return this.nested.check(adapter, scope, table, val);

		return ExpressionResult.ACCEPTED;
	}

	@Override
	public void init(String table, RecordStruct where) throws OperatingContextException {
		this.table = table;
		this.lang = where.getFieldAsString("Locale", OperationContext.getOrThrow().getLocale());
		
		this.fieldInfo = ExpressionUtil.loadField(table, where.getFieldAsRecord("A"));
		
		if (this.fieldInfo == null) {
			Logger.error("Contains is missing a field");
			return;
		}
	}
	
	@Override
	public void parse(IParentAwareWork state, XElement code, RecordStruct clause) throws OperatingContextException {
		clause.with("A", Query.createWhereField(state, code));
	}
}
